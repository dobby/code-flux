package com.company.throughput.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient

class SyncStateRepositoryTests {
    @Test
    fun `abandon incomplete runs closes stale running entries`() {
        val context = TestDatabaseSupport.createContext()
        val repository = SyncStateRepository(context.jdbcClient)

        val syncRunId = repository.startRun()
        repository.abandonIncompleteRuns("Recovered stale sync run from an earlier worker failure")

        val row = context.jdbcClient.sql(
            """
            SELECT status, finished_at, message
            FROM sync_run
            WHERE sync_run_id = :syncRunId
            """.trimIndent(),
        )
            .param("syncRunId", syncRunId)
            .query { rs, _ ->
                Triple(
                    rs.getString("status"),
                    rs.getString("finished_at"),
                    rs.getString("message"),
                )
            }
            .single()

        assertEquals("ABANDONED", row.first)
        assertNotNull(row.second)
        assertEquals("Recovered stale sync run from an earlier worker failure", row.third)
    }
}

private object TestDatabaseSupport {
    fun createContext(): TestDatabaseContext {
        val project = com.company.throughput.git.GitFixtureSupport.createProject(
            kotlin.io.path.createTempDirectory("sync-state-repo-project"),
        )
        val dataDir = kotlin.io.path.createTempDirectory("sync-state-repo-data")
        val appContext = com.company.throughput.git.GitFixtureSupport.createAppContext(project, dataDir)
        return TestDatabaseContext(appContext.jdbcClient)
    }
}

private data class TestDatabaseContext(
    val jdbcClient: JdbcClient,
)
