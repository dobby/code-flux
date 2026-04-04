package com.company.throughput.support

import com.company.throughput.config.ResolvedDashboardConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.util.Locale

private val logger = KotlinLogging.logger {}

@Component
class BrowserLaunchService(
    private val resolvedDashboardConfig: ResolvedDashboardConfig,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        if (!resolvedDashboardConfig.app.openBrowserOnStart) {
            return
        }

        runCatching {
            launchBrowser(resolvedDashboardConfig.app.baseUrl)
        }.onFailure { exception ->
            logger.warn(exception) { "Failed to auto-open browser" }
        }
    }

    private fun launchBrowser(url: String) {
        val osName = System.getProperty("os.name").orEmpty().lowercase(Locale.ENGLISH)
        val command = when {
            osName.contains("mac") -> listOf("open", url)
            osName.contains("win") -> listOf("cmd", "/c", "start", "", url)
            osName.contains("nux") || osName.contains("nix") -> listOf("xdg-open", url)
            else -> {
                logger.info { "No supported browser launcher for os.name=$osName; skipping browser auto-open" }
                return
            }
        }

        ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
    }
}
