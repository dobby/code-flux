package com.company.throughput.sync

import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class CurrentSyncStatus(
    val syncRunId: Long,
    val totalRepos: Int,
    val completedRepos: Int,
    val currentRepoId: String?,
    val stage: String,
    val stopRequested: Boolean,
) {
    val progressPercent: Int
        get() = if (totalRepos <= 0) 0 else ((completedRepos * 100.0) / totalRepos).toInt()
}

@Component
class SyncRuntimeState {
    private val running = AtomicBoolean(false)
    private val currentRunId = AtomicLong(0)
    private val totalRepos = AtomicInteger(0)
    private val completedRepos = AtomicInteger(0)
    private val currentRepoId = AtomicReference<String?>(null)
    private val stage = AtomicReference("Idle")
    private val stopRequested = AtomicBoolean(false)

    fun tryStart(runId: Long, repoCount: Int): Boolean {
        val started = running.compareAndSet(false, true)
        if (started) {
            currentRunId.set(runId)
            totalRepos.set(repoCount)
            completedRepos.set(0)
            currentRepoId.set(null)
            stage.set("Queued")
            stopRequested.set(false)
        }
        return started
    }

    fun markRepoStarted(repoId: String) {
        currentRepoId.set(repoId)
        stage.set(
            if (stopRequested.get()) {
                "Stopping after current repository"
            } else {
                "Syncing repository"
            },
        )
    }

    fun markRepoCompleted() {
        completedRepos.incrementAndGet()
        currentRepoId.set(null)
        stage.set(
            if (stopRequested.get()) {
                "Stopping after current repository"
            } else {
                "Preparing next repository"
            },
        )
    }

    fun markStage(value: String) {
        stage.set(value)
    }

    fun requestStop(): Boolean {
        if (!running.get()) {
            return false
        }
        stopRequested.set(true)
        stage.set("Stopping after current repository")
        return true
    }

    fun isStopRequested(): Boolean = stopRequested.get()

    fun finish() {
        currentRunId.set(0)
        totalRepos.set(0)
        completedRepos.set(0)
        currentRepoId.set(null)
        stage.set("Idle")
        stopRequested.set(false)
        running.set(false)
    }

    fun isRunning(): Boolean = running.get()

    fun currentRunId(): Long? = currentRunId.get().takeIf { it > 0 }

    fun currentStatus(): CurrentSyncStatus? {
        if (!running.get()) {
            return null
        }
        val runId = currentRunId.get()
        if (runId <= 0) {
            return null
        }
        return CurrentSyncStatus(
            syncRunId = runId,
            totalRepos = totalRepos.get(),
            completedRepos = completedRepos.get(),
            currentRepoId = currentRepoId.get(),
            stage = stage.get(),
            stopRequested = stopRequested.get(),
        )
    }
}
