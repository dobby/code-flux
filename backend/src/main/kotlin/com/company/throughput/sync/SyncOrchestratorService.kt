package com.company.throughput.sync

import com.company.throughput.classify.ClassificationService
import com.company.throughput.config.RepoConfig
import com.company.throughput.config.ResolvedDashboardConfig
import com.company.throughput.dedupe.CanonicalCommitCandidate
import com.company.throughput.dedupe.CanonicalPatchService
import com.company.throughput.git.AuthorMatcher
import com.company.throughput.git.GitEnvironmentService
import com.company.throughput.git.GitHistoryExtractor
import com.company.throughput.mirror.MirrorSyncService
import com.company.throughput.persistence.AggregationService
import com.company.throughput.persistence.CommitFactInput
import com.company.throughput.persistence.RawFactRepository
import com.company.throughput.persistence.SyncStateRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private val logger = KotlinLogging.logger {}

data class SyncRunSummary(
    val syncRunId: Long,
    val status: String,
)

@Service
class SyncOrchestratorService(
    private val resolvedDashboardConfig: ResolvedDashboardConfig,
    private val syncStateRepository: SyncStateRepository,
    private val syncRuntimeState: SyncRuntimeState,
    private val gitEnvironmentService: GitEnvironmentService,
    private val mirrorSyncService: MirrorSyncService,
    private val gitHistoryExtractor: GitHistoryExtractor,
    private val authorMatcher: AuthorMatcher,
    private val canonicalPatchService: CanonicalPatchService,
    private val classificationService: ClassificationService,
    private val rawFactRepository: RawFactRepository,
    private val aggregationService: AggregationService,
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun requestIncrementalSync(): SyncRunSummary {
        val enabledRepos = resolvedDashboardConfig.repos.filter { it.enabled }
        if (!syncRuntimeState.isRunning()) {
            syncStateRepository.abandonIncompleteRuns("Recovered stale sync run from an earlier worker failure")
        }

        val syncRunId = syncStateRepository.startRun()
        if (!syncRuntimeState.tryStart(syncRunId, enabledRepos.size)) {
            syncStateRepository.finishRun(syncRunId, "REJECTED", "A sync is already running")
            return SyncRunSummary(syncRunId = syncRunId, status = "REJECTED")
        }

        executor.submit {
            try {
                executeIncrementalSync(syncRunId)
            } catch (exception: Exception) {
                logger.error(exception) { "Sync run $syncRunId crashed before completion" }
                syncStateRepository.finishRun(syncRunId, "FAILED", exception.message ?: "Unhandled sync failure")
            } finally {
                syncRuntimeState.finish()
            }
        }

        return SyncRunSummary(syncRunId = syncRunId, status = "STARTED")
    }

    fun requestStop(): Boolean = syncRuntimeState.requestStop()

    @Transactional
    fun executeIncrementalSync(syncRunId: Long): SyncRunSummary {
        gitEnvironmentService.verify()
        var hadFailures = false
        var cancelled = false
        val enabledRepos = resolvedDashboardConfig.repos.filter { it.enabled }

        enabledRepos.forEach { repo ->
            if (syncRuntimeState.isStopRequested()) {
                cancelled = true
                return@forEach
            }

            syncRuntimeState.markRepoStarted(repo.id)
            try {
                processRepo(syncRunId, repo)
            } catch (exception: Exception) {
                hadFailures = true
                logger.error(exception) { "Failed to sync repo ${repo.id}" }
                syncStateRepository.markRepoFailure(repo.id, exception.message ?: "Unknown sync failure")
            } finally {
                syncRuntimeState.markRepoCompleted()
            }
        }

        val status = when {
            cancelled || syncRuntimeState.isStopRequested() -> "CANCELLED"
            hadFailures -> "PARTIAL_FAILURE"
            else -> "SUCCESS"
        }
        val message = if (status == "CANCELLED") "Sync stopped by user" else null
        syncStateRepository.finishRun(syncRunId, status, message)
        return SyncRunSummary(syncRunId = syncRunId, status = status)
    }

    private fun processRepo(syncRunId: Long, repo: RepoConfig) {
        val mirrorPath = mirrorSyncService.ensureMirror(repo)
        val refs = gitHistoryExtractor.resolveRefs(repo, mirrorPath)
        val commitShas = gitHistoryExtractor.enumerateCommitShas(mirrorPath, refs)
        val impactedDays = linkedSetOf<LocalDate>()
        val processedCommitShas = linkedSetOf<String>()

        commitShas.forEach { commitSha ->
            if (!processedCommitShas.add(commitSha)) {
                return@forEach
            }
            if (rawFactRepository.commitExists(repo.id, commitSha)) {
                return@forEach
            }

            val metadata = gitHistoryExtractor.readMetadata(mirrorPath, commitSha)
            if (!resolvedDashboardConfig.syncWindow.contains(metadata.authoredAt.toLocalDate())) {
                return@forEach
            }
            val patchId = gitHistoryExtractor.computePatchId(mirrorPath, commitSha)
            val existingCandidates = patchId?.let { rawFactRepository.findPatchCandidates(repo.id, it) }.orEmpty()
            val currentCandidate = CanonicalCommitCandidate(
                commitSha = metadata.commitSha,
                authoredAt = metadata.authoredAt,
                committedAt = metadata.committedAt,
            )
            val canonicalCandidate = canonicalPatchService.chooseCanonical(existingCandidates + currentCandidate)
            val previousCanonical = existingCandidates.takeIf { it.isNotEmpty() }?.let(canonicalPatchService::chooseCanonical)
            val authorId = authorMatcher.matchAuthorId(metadata.authorName, metadata.authorEmail)

            try {
                rawFactRepository.insertCommit(
                    CommitFactInput(
                        repoId = repo.id,
                        commitSha = metadata.commitSha,
                        authorId = authorId,
                        authorName = metadata.authorName,
                        authorEmail = metadata.authorEmail,
                        authoredAt = metadata.authoredAt,
                        committedAt = metadata.committedAt,
                        subject = metadata.subject,
                        patchId = patchId,
                        isMergeCommit = false,
                        isDuplicatePatch = canonicalCandidate.commitSha != metadata.commitSha,
                        canonicalCommitSha = canonicalCandidate.commitSha,
                        syncRunId = syncRunId,
                    ),
                )
            } catch (_: DataIntegrityViolationException) {
                logger.warn { "Skipping duplicate commit ${metadata.commitSha} for repo ${repo.id}" }
                return@forEach
            }

            val classifiedFiles = gitHistoryExtractor.readFileStats(mirrorPath, commitSha)
                .map { classificationService.classify(repo, it) }
            rawFactRepository.insertCommitFiles(
                repoId = repo.id,
                commitSha = metadata.commitSha,
                fileStats = classifiedFiles,
                isCanonicalPatch = canonicalCandidate.commitSha == metadata.commitSha,
            )

            if (patchId != null) {
                rawFactRepository.applyCanonicalDecision(repo.id, patchId, canonicalCandidate.commitSha)
            }

            if (authorId != null && canonicalCandidate.commitSha == metadata.commitSha) {
                impactedDays += metadata.authoredAt.toLocalDate()
            }
            if (previousCanonical != null && previousCanonical.commitSha != canonicalCandidate.commitSha) {
                impactedDays += previousCanonical.authoredAt.toLocalDate()
            }
        }

        aggregationService.rebuildImpacted(repo.id, impactedDays)
        syncStateRepository.markRepoSuccess(repo.id, syncRunId, commitShas.lastOrNull())
    }
}
