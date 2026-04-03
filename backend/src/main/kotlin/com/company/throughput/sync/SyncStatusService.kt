package com.company.throughput.sync

import com.company.throughput.persistence.RepoSyncStateRecord
import com.company.throughput.persistence.SyncRunRecord
import com.company.throughput.persistence.SyncStateRepository
import org.springframework.stereotype.Service
import java.time.Instant

data class SyncRunStatusDto(
    val syncRunId: Long,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val status: String,
    val message: String?,
)

data class CurrentSyncStatusDto(
    val syncRunId: Long,
    val totalRepos: Int,
    val completedRepos: Int,
    val currentRepoId: String?,
    val stage: String,
    val stopRequested: Boolean,
    val progressPercent: Int,
)

data class RepoSyncStatusDto(
    val repoId: String,
    val status: String,
    val lastSuccessfulSyncedAt: Instant?,
    val lastErrorMessage: String?,
)

data class SyncStatusResponse(
    val running: Boolean,
    val current: CurrentSyncStatusDto?,
    val lastRun: SyncRunStatusDto?,
    val repos: List<RepoSyncStatusDto>,
)

@Service
class SyncStatusService(
    private val syncStateRepository: SyncStateRepository,
    private val syncRuntimeState: SyncRuntimeState,
) {
    fun currentStatus(): SyncStatusResponse {
        val runtimeStatus = syncRuntimeState.currentStatus()
        val currentRepoId = runtimeStatus?.currentRepoId
        return SyncStatusResponse(
            running = syncRuntimeState.isRunning(),
            current = runtimeStatus?.toDto(),
            lastRun = syncStateRepository.latestRun()?.toDto(),
            repos = syncStateRepository.repoStates().map { it.toDto(currentRepoId) },
        )
    }

    private fun SyncRunRecord.toDto(): SyncRunStatusDto = SyncRunStatusDto(
        syncRunId = syncRunId,
        startedAt = startedAt,
        finishedAt = finishedAt,
        status = status,
        message = message,
    )

    private fun CurrentSyncStatus.toDto(): CurrentSyncStatusDto = CurrentSyncStatusDto(
        syncRunId = syncRunId,
        totalRepos = totalRepos,
        completedRepos = completedRepos,
        currentRepoId = currentRepoId,
        stage = stage,
        stopRequested = stopRequested,
        progressPercent = progressPercent,
    )

    private fun RepoSyncStateRecord.toDto(currentRepoId: String?): RepoSyncStatusDto = RepoSyncStatusDto(
        repoId = repoId,
        status = if (repoId == currentRepoId) "RUNNING" else status,
        lastSuccessfulSyncedAt = lastSuccessfulSyncedAt,
        lastErrorMessage = lastErrorMessage,
    )
}
