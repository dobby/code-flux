package com.company.throughput.persistence

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.flywaydb.core.Flyway
import org.springframework.jdbc.core.simple.JdbcClient

@SpringBootTest
class FlywayMigrationTests(
    @param:Autowired private val jdbcClient: JdbcClient,
    @param:Autowired private val flyway: Flyway,
) {
    @Test
    fun `core tables exist after startup`() {
        val tables = jdbcClient.sql(
            """
            SELECT name
            FROM sqlite_master
            WHERE type = 'table'
            """.trimIndent(),
        )
            .query { rs, _ -> rs.getString("name") }
            .list()
            .toSet()

        assertTrue(tables.contains("repository"))
        assertTrue(tables.contains("daily_fact"))
        assertTrue(tables.contains("annotation"))
    }

    @Test
    fun `migrations are safe to rerun`() {
        val migrations = flyway.migrate()
        assertTrue(migrations.migrationsExecuted == 0)
    }
}
