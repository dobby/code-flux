package com.company.throughput.persistence

import com.company.throughput.config.ResolvedDashboardConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.jdbc.core.simple.JdbcClient
import javax.sql.DataSource
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

@Configuration
class PersistenceConfiguration {
    @Bean
    fun dataSource(databasePath: Path): DataSource {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:${databasePath}"
            driverClassName = "org.sqlite.JDBC"
            maximumPoolSize = 1
            connectionTestQuery = "SELECT 1"
            poolName = "code-flux-sqlite"
        }
        return HikariDataSource(hikariConfig)
    }

    @Bean
    fun jdbcClient(dataSource: DataSource): JdbcClient = JdbcClient.create(dataSource)

    @Bean
    @Order(0)
    fun sqlitePragmasInitializer(dataSource: DataSource): ApplicationRunner = ApplicationRunner {
        SqlitePragmasInitializer(dataSource).apply()
    }

    @Bean
    @Order(1)
    fun metadataSeeder(
        jdbcClient: JdbcClient,
        resolvedDashboardConfig: ResolvedDashboardConfig,
    ): ApplicationRunner = ApplicationRunner {
        MetadataSeeder(jdbcClient, resolvedDashboardConfig).seed()
    }
}

class SqlitePragmasInitializer(
    private val dataSource: DataSource,
) {
    fun apply() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA foreign_keys = ON")
                statement.execute("PRAGMA journal_mode = WAL")
                statement.execute("PRAGMA busy_timeout = 5000")
            }
        }
        logger.info { "Initialized SQLite pragmas" }
    }
}
