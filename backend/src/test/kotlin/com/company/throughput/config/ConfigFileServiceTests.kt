package com.company.throughput.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import java.nio.file.Files

class ConfigFileServiceTests {
    @Test
    fun `read redacts secrets and save preserves masked values`() {
        val tempDir = Files.createTempDirectory("code-flux-config-tests")
        val configPath = tempDir.resolve("config.yaml")
        Files.writeString(
            configPath,
            """
            app:
              baseUrl: "http://127.0.0.1:8086"
              dataDir: "${tempDir.resolve("runtime")}"
              openBrowserOnStart: false
              logLevel: "INFO"
            git:
              executable: "git"
              mirrorDir: "${tempDir.resolve("mirrors")}"
              timeoutSeconds: 120
              includeMergeCommits: false
              deduplicateByPatchId: true
              useAuthoredDate: true
              auth:
                httpUsername: "oauth2"
                httpToken: "super-secret-token"
            repos:
              - id: "fixture"
                displayName: "Fixture"
                cloneUrl: "https://example.com/repo.git"
                enabled: true
                productCode: "FIXTURE"
                branchPatterns:
                  - "main"
            authors:
              include:
                - id: "eli"
                  displayName: "Eli"
                  emails:
                    - "eli@example.com"
                  names:
                    - "Eli"
            classification:
              languageByExtension:
                kt: "kotlin"
              rules: []
            uiDefaults:
              defaultMetric: "lines_added"
              defaultGroupBy: "author"
              defaultChartLibrary: "echarts"
              defaultIncludeCategories:
                - "production"
              defaultExcludeCategories:
                - "test"
            syncWindow: {}
            """.trimIndent(),
        )

        val validator = LocalValidatorFactoryBean()
        validator.afterPropertiesSet()

        val service = ConfigFileService(
            environment = MockEnvironment().withProperty("APP_CONFIG_FILE", configPath.toString()),
            validator = validator,
        )

        val snapshot = service.readConfigFile()
        assertFalse(snapshot.yaml.contains("super-secret-token"))
        assertTrue(snapshot.yaml.contains(ConfigFileService.redactedSecretPlaceholder))

        service.saveConfigFile(snapshot.yaml.replace("INFO", "DEBUG"))

        val savedYaml = Files.readString(configPath)
        assertTrue(savedYaml.contains("logLevel: \"DEBUG\""))
        assertTrue(savedYaml.contains("httpToken: \"super-secret-token\""))
    }
}
