package com.company.throughput.support

import com.company.throughput.config.ResolvedDashboardConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.awt.Desktop
import java.net.URI

private val logger = KotlinLogging.logger {}

@Component
class BrowserLaunchService(
    private val resolvedDashboardConfig: ResolvedDashboardConfig,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        if (!resolvedDashboardConfig.app.openBrowserOnStart) {
            return
        }

        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            logger.info { "Desktop browse action is not supported; skipping browser auto-open" }
            return
        }

        runCatching {
            Desktop.getDesktop().browse(URI.create(resolvedDashboardConfig.app.baseUrl))
        }.onFailure { exception ->
            logger.warn(exception) { "Failed to auto-open browser" }
        }
    }
}
