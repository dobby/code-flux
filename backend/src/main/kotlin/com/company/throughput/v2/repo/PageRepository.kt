package com.company.throughput.v2.repo

import com.company.throughput.v2.model.DashboardPage
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class PageRepository(
    private val jdbcClient: JdbcClient,
) {
    fun list(includeArchived: Boolean = false): List<DashboardPage> {
        val sql = buildString {
            append(
                """
                SELECT id, slug, title, description, icon, sort_order, archived, created_at, updated_at
                FROM dashboard_pages
                """.trimIndent(),
            )
            if (!includeArchived) {
                append("\nWHERE archived = 0")
            }
            append("\nORDER BY sort_order ASC, created_at ASC")
        }
        return jdbcClient.sql(sql)
            .query { rs, _ ->
                DashboardPage(
                    id = rs.getString("id"),
                    slug = rs.getString("slug"),
                    title = rs.getString("title"),
                    description = rs.getString("description"),
                    icon = rs.getString("icon"),
                    sortOrder = rs.getInt("sort_order"),
                    archived = rs.getInt("archived") == 1,
                    createdAt = parseInstant(rs.getString("created_at")),
                    updatedAt = parseInstant(rs.getString("updated_at")),
                )
            }
            .list()
    }

    fun findById(id: String): DashboardPage? =
        jdbcClient.sql(
            """
            SELECT id, slug, title, description, icon, sort_order, archived, created_at, updated_at
            FROM dashboard_pages
            WHERE id = :id
            """.trimIndent(),
        )
            .param("id", id)
            .query { rs, _ ->
                DashboardPage(
                    id = rs.getString("id"),
                    slug = rs.getString("slug"),
                    title = rs.getString("title"),
                    description = rs.getString("description"),
                    icon = rs.getString("icon"),
                    sortOrder = rs.getInt("sort_order"),
                    archived = rs.getInt("archived") == 1,
                    createdAt = parseInstant(rs.getString("created_at")),
                    updatedAt = parseInstant(rs.getString("updated_at")),
                )
            }
            .optional()
            .orElse(null)

    fun create(title: String, description: String?, icon: String?): DashboardPage {
        val id = newId("page")
        val slug = nextSlug(title)
        val now = Instant.now()
        val nextOrder = nextSortOrder()
        jdbcClient.sql(
            """
            INSERT INTO dashboard_pages (
              id, slug, title, description, icon, sort_order, archived, created_at, updated_at
            ) VALUES (
              :id, :slug, :title, :description, :icon, :sortOrder, 0, :createdAt, :updatedAt
            )
            """.trimIndent(),
        )
            .param("id", id)
            .param("slug", slug)
            .param("title", title)
            .param("description", description)
            .param("icon", icon)
            .param("sortOrder", nextOrder)
            .param("createdAt", now.toString())
            .param("updatedAt", now.toString())
            .update()
        return requireNotNull(findById(id))
    }

    fun update(id: String, title: String?, description: String?, icon: String?): DashboardPage? {
        val current = findById(id) ?: return null
        val nextTitle = title ?: current.title
        val nextSlug = if (title != null && title != current.title) nextSlug(title, current.id) else current.slug
        val now = Instant.now()
        jdbcClient.sql(
            """
            UPDATE dashboard_pages
            SET slug = :slug,
                title = :title,
                description = :description,
                icon = :icon,
                updated_at = :updatedAt
            WHERE id = :id
            """.trimIndent(),
        )
            .param("id", id)
            .param("slug", nextSlug)
            .param("title", nextTitle)
            .param("description", description ?: current.description)
            .param("icon", icon ?: current.icon)
            .param("updatedAt", now.toString())
            .update()
        return findById(id)
    }

    fun archive(id: String): Boolean =
        jdbcClient.sql(
            """
            UPDATE dashboard_pages
            SET archived = 1, updated_at = :updatedAt
            WHERE id = :id
            """.trimIndent(),
        )
            .param("id", id)
            .param("updatedAt", Instant.now().toString())
            .update() > 0

    fun reorder(pageIds: List<String>) {
        val now = Instant.now().toString()
        pageIds.forEachIndexed { index, pageId ->
            jdbcClient.sql(
                """
                UPDATE dashboard_pages
                SET sort_order = :sortOrder, updated_at = :updatedAt
                WHERE id = :id
                """.trimIndent(),
            )
                .param("sortOrder", index)
                .param("updatedAt", now)
                .param("id", pageId)
                .update()
        }
    }

    private fun nextSortOrder(): Int =
        jdbcClient.sql("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM dashboard_pages")
            .query(Int::class.java)
            .single()

    private fun nextSlug(title: String, excludingId: String? = null): String {
        val base = slugify(title)
        var candidate = base
        var suffix = 2
        while (slugExists(candidate, excludingId)) {
            candidate = "$base-$suffix"
            suffix += 1
        }
        return candidate
    }

    private fun slugExists(slug: String, excludingId: String?): Boolean {
        val sql = buildString {
            append("SELECT COUNT(*) FROM dashboard_pages WHERE slug = :slug")
            if (excludingId != null) {
                append(" AND id != :excludingId")
            }
        }
        var spec = jdbcClient.sql(sql).param("slug", slug)
        if (excludingId != null) {
            spec = spec.param("excludingId", excludingId)
        }
        return spec.query(Int::class.java).single() > 0
    }
}
