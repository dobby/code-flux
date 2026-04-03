package com.company.throughput.git

import com.company.throughput.config.ResolvedDashboardConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private val logger = KotlinLogging.logger {}

data class GitCommandResult(
    val command: List<String>,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val duration: Duration,
)

open class GitCommandException(
    message: String,
    val command: List<String>,
    val exitCode: Int?,
    val stdout: String,
    val stderr: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class GitCommandTimeoutException(
    command: List<String>,
    timeout: Duration,
    stdout: String,
    stderr: String,
) : GitCommandException(
    message = "Git command timed out after ${timeout.seconds}s: ${command.joinToString(" ")}",
    command = command,
    exitCode = null,
    stdout = stdout,
    stderr = stderr,
    cause = TimeoutException("Command timed out after $timeout"),
)

@Component
class GitCommandRunner(
    private val resolvedDashboardConfig: ResolvedDashboardConfig,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun runGit(
        args: List<String>,
        workingDirectory: Path? = null,
        stdin: String? = null,
    ): GitCommandResult = runCommand(
        command = listOf(resolvedDashboardConfig.git.executable) + args,
        workingDirectory = workingDirectory,
        stdin = stdin,
        timeout = Duration.ofSeconds(resolvedDashboardConfig.git.timeoutSeconds.toLong()),
    )

    fun runCommand(
        command: List<String>,
        workingDirectory: Path? = null,
        stdin: String? = null,
        timeout: Duration,
    ): GitCommandResult {
        require(command.isNotEmpty()) { "Command must not be empty" }

        val startedAt = Instant.now(clock)
        val askPassScript = resolvedDashboardConfig.git.auth?.let(::createAskPassScript)
        val process = ProcessBuilder(command)
            .apply {
                if (workingDirectory != null) {
                    directory(workingDirectory.toFile())
                }
                environment()["GIT_TERMINAL_PROMPT"] = "0"
                environment()["GCM_INTERACTIVE"] = "never"
                if (askPassScript != null) {
                    environment()["GIT_ASKPASS"] = askPassScript.path.toString()
                    environment()["SSH_ASKPASS"] = askPassScript.path.toString()
                    environment()["CODE_FLUX_GIT_HTTP_USERNAME"] = askPassScript.username
                    environment()["CODE_FLUX_GIT_HTTP_TOKEN"] = askPassScript.token
                }
            }
            .start()

        val executor = Executors.newFixedThreadPool(2)
        try {
            if (stdin != null) {
                process.outputStream.use { output ->
                    output.write(stdin.toByteArray(StandardCharsets.UTF_8))
                }
            } else {
                process.outputStream.close()
            }

            val stdoutFuture = executor.submit<String> {
                process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            }
            val stderrFuture = executor.submit<String> {
                process.errorStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            }

            val completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)
            val stdout = stdoutFuture.get()
            val stderr = stderrFuture.get()
            val duration = Duration.between(startedAt, Instant.now(clock))

            if (!completed) {
                process.destroyForcibly()
                throw GitCommandTimeoutException(command = command, timeout = timeout, stdout = stdout, stderr = stderr)
            }

            val result = GitCommandResult(
                command = command,
                exitCode = process.exitValue(),
                stdout = stdout,
                stderr = stderr,
                duration = duration,
            )

            if (result.exitCode != 0) {
                throw GitCommandException(
                    message = "Git command failed with exit code ${result.exitCode}: ${command.joinToString(" ")}",
                    command = command,
                    exitCode = result.exitCode,
                    stdout = result.stdout,
                    stderr = result.stderr,
                )
            }

            logger.debug { "Executed command in ${result.duration.toMillis()}ms: ${command.joinToString(" ")}" }
            return result
        } finally {
            executor.shutdownNow()
            askPassScript?.cleanup()
        }
    }

    private fun createAskPassScript(auth: com.company.throughput.config.ResolvedGitAuthProperties): AskPassScript {
        val path = Files.createTempFile("code-flux-git-askpass", ".sh")
        Files.writeString(
            path,
            """
            #!/bin/sh
            prompt="${'$'}1"
            case "${'$'}prompt" in
              *Username*|*username*)
                printf '%s\n' "${'$'}CODE_FLUX_GIT_HTTP_USERNAME"
                ;;
              *Password*|*password*)
                printf '%s\n' "${'$'}CODE_FLUX_GIT_HTTP_TOKEN"
                ;;
              *)
                printf '\n'
                ;;
            esac
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )
        runCatching {
            Files.setPosixFilePermissions(
                path,
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                ),
            )
        }
        return AskPassScript(path = path, username = auth.httpUsername, token = auth.httpToken)
    }
}

private data class AskPassScript(
    val path: Path,
    val username: String,
    val token: String,
) {
    fun cleanup() {
        runCatching { Files.deleteIfExists(path) }
    }
}
