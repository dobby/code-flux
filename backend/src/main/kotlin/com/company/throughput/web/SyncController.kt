package com.company.throughput.web

import com.company.throughput.sync.SyncOrchestratorService
import com.company.throughput.sync.SyncStatusResponse
import com.company.throughput.sync.SyncStatusService
import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class SyncRunRequest(
    @field:Pattern(regexp = "incremental", message = "mode must be incremental")
    val mode: String = "incremental",
)

data class SyncRunResponse(
    val accepted: Boolean,
    val syncRunId: Long,
    val status: String,
)

data class SyncStopResponse(
    val accepted: Boolean,
    val running: Boolean,
    val status: String,
)

data class SyncResetResponse(
    val accepted: Boolean,
    val running: Boolean,
    val status: String,
)

@Validated
@RestController
@RequestMapping("/api/sync")
class SyncController(
    private val syncStatusService: SyncStatusService,
    private val syncOrchestratorService: SyncOrchestratorService,
) {
    @GetMapping("/status")
    fun status(): SyncStatusResponse = syncStatusService.currentStatus()

    @PostMapping("/run")
    fun run(@Valid @RequestBody request: SyncRunRequest): SyncRunResponse {
        require(request.mode == "incremental") { "Only incremental sync is supported in v1" }
        val result = syncOrchestratorService.requestIncrementalSync()
        return SyncRunResponse(
            accepted = result.status != "REJECTED",
            syncRunId = result.syncRunId,
            status = result.status,
        )
    }

    @PostMapping("/stop")
    fun stop(): SyncStopResponse {
        val accepted = syncOrchestratorService.requestStop()
        return SyncStopResponse(
            accepted = accepted,
            running = syncStatusService.currentStatus().running,
            status = if (accepted) "STOP_REQUESTED" else "IDLE",
        )
    }

    @PostMapping("/reset")
    fun reset(): SyncResetResponse {
        val accepted = syncOrchestratorService.resetSyncData()
        return SyncResetResponse(
            accepted = accepted,
            running = syncStatusService.currentStatus().running,
            status = if (accepted) "RESET" else "RUNNING",
        )
    }
}
