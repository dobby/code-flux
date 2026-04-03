package com.company.throughput.git

import com.company.throughput.config.ResolvedDashboardConfig
import org.springframework.stereotype.Service
import java.util.Locale

@Service
class AuthorMatcher(
    resolvedDashboardConfig: ResolvedDashboardConfig,
) {
    private val authorByEmail: Map<String, String>
    private val authorByName: Map<String, String>

    init {
        val emailMap = mutableMapOf<String, String>()
        val nameMap = mutableMapOf<String, String>()

        resolvedDashboardConfig.authors.forEach { author ->
            author.emails.forEach { email ->
                emailMap[email.trim().lowercase(Locale.getDefault())] = author.id
            }
            nameMap[normalizeName(author.displayName)] = author.id
            author.names.forEach { name ->
                nameMap[normalizeName(name)] = author.id
            }
        }

        authorByEmail = emailMap
        authorByName = nameMap
    }

    fun matchAuthorId(authorName: String, authorEmail: String?): String? {
        val normalizedEmail = authorEmail?.trim()?.lowercase(Locale.getDefault())
        if (!normalizedEmail.isNullOrBlank()) {
            authorByEmail[normalizedEmail]?.let { return it }
        }
        return authorByName[normalizeName(authorName)]
    }

    private fun normalizeName(name: String): String =
        name.trim()
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.getDefault())
}
