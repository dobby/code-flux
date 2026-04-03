package com.company.throughput.config

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object GitExecutableLocator {
    fun resolve(executable: String): Path? {
        val trimmed = executable.trim()
        if (trimmed.isBlank()) {
            return null
        }

        val directPath = Paths.get(trimmed)
        if (trimmed.contains("/") || directPath.isAbsolute) {
            return directPath.takeIf { Files.isExecutable(it) }
        }

        val pathEntries = System.getenv("PATH")
            ?.split(":")
            .orEmpty()
            .map(String::trim)
            .filter(String::isNotBlank)

        return pathEntries
            .asSequence()
            .map { Paths.get(it).resolve(trimmed) }
            .firstOrNull { Files.isExecutable(it) }
    }
}
