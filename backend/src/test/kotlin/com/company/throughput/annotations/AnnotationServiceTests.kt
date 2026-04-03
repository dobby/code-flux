package com.company.throughput.annotations

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Files
import java.time.LocalDate

@SpringBootTest
class AnnotationServiceTests(
    @param:Autowired private val jdbcClient: JdbcClient,
    @param:Autowired private val annotationService: AnnotationService,
) {
    companion object {
        private val testDataDir = Files.createTempDirectory("code-flux-annotation-tests")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("app.dataDir") { testDataDir.toString() }
            registry.add("git.mirrorDir") { testDataDir.resolve("mirrors").toString() }
            registry.add("app.openBrowserOnStart") { "false" }
            registry.add("git.auth.httpToken") { "" }
        }
    }

    @BeforeEach
    fun resetTable() {
        jdbcClient.sql("DELETE FROM annotation").update()
    }

    @Test
    fun `create list update and delete annotation`() {
        val created = annotationService.create(
            CreateAnnotationRequest(
                day = LocalDate.parse("2026-03-15"),
                title = " Agentic rollout ",
                description = "Enabled copilots on all delivery squads",
                type = AnnotationType.ADOPTION,
                colorToken = "accent",
            ),
        )

        assertEquals("Agentic rollout", created.title)

        val listed = annotationService.list(
            from = LocalDate.parse("2026-03-01"),
            to = LocalDate.parse("2026-03-31"),
        )
        assertEquals(1, listed.size)
        assertEquals(created.annotationId, listed.first().annotationId)

        val updated = annotationService.update(
            created.annotationId,
            UpdateAnnotationRequest(
                title = " Agentic rollout complete ",
                description = "Updated note",
                type = AnnotationType.PROCESS_CHANGE,
                colorToken = "warning",
            ),
        )

        assertEquals("Agentic rollout complete", updated.title)
        assertEquals(AnnotationType.PROCESS_CHANGE, updated.type)

        annotationService.delete(created.annotationId)

        assertEquals(0, annotationService.list(null, null).size)
    }

    @Test
    fun `updating or deleting missing annotation fails clearly`() {
        assertThrows(IllegalArgumentException::class.java) {
            annotationService.update(
                9999,
                UpdateAnnotationRequest(
                    title = "Missing",
                    description = null,
                    type = AnnotationType.CUSTOM,
                    colorToken = null,
                ),
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            annotationService.delete(9999)
        }
    }
}
