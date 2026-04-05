package com.company.throughput.v2.repo

import com.company.throughput.v2.model.DatasetKey
import com.company.throughput.v2.model.PageWidgetInstance
import com.company.throughput.v2.model.WidgetDefinition
import com.company.throughput.v2.model.WidgetKind
import com.company.throughput.v2.model.WidgetQuerySpec
import com.company.throughput.v2.model.WidgetVizSpec
import com.company.throughput.v2.model.LayoutSpec
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class WidgetDefinitionRepository(
    private val jdbcClient: JdbcClient,
    private val objectMapper: ObjectMapper,
) {
    fun list(includeArchived: Boolean = false): List<WidgetDefinition> {
        val sql = buildString {
            append(
                """
                SELECT id, slug, kind, title, description, dataset_key, tags_json, query_spec_json, viz_spec_json,
                       is_system, archived, version, created_at, updated_at
                FROM widget_definitions
                """.trimIndent(),
            )
            if (!includeArchived) {
                append("\nWHERE archived = 0")
            }
            append("\nORDER BY is_system DESC, title ASC")
        }
        return jdbcClient.sql(sql).query { rs, _ -> mapDefinition(rs) }.list()
    }

    fun findById(id: String): WidgetDefinition? =
        jdbcClient.sql(
            """
            SELECT id, slug, kind, title, description, dataset_key, tags_json, query_spec_json, viz_spec_json,
                   is_system, archived, version, created_at, updated_at
            FROM widget_definitions
            WHERE id = :id
            """.trimIndent(),
        )
            .param("id", id)
            .query { rs, _ -> mapDefinition(rs) }
            .optional()
            .orElse(null)

    fun create(
        title: String,
        description: String?,
        tags: List<String>,
        kind: WidgetKind,
        datasetKey: DatasetKey?,
        querySpec: WidgetQuerySpec?,
        vizSpec: WidgetVizSpec,
        isSystem: Boolean = false,
    ): WidgetDefinition {
        val id = newId("widget")
        val slug = nextSlug(title)
        val now = Instant.now().toString()
        jdbcClient.sql(
            """
            INSERT INTO widget_definitions (
              id, slug, kind, title, description, dataset_key, tags_json, query_spec_json, viz_spec_json,
              is_system, archived, version, created_at, updated_at
            ) VALUES (
              :id, :slug, :kind, :title, :description, :datasetKey, :tagsJson, :querySpecJson, :vizSpecJson,
              :isSystem, 0, 1, :createdAt, :updatedAt
            )
            """.trimIndent(),
        )
            .param("id", id)
            .param("slug", slug)
            .param("kind", kind.name.lowercase())
            .param("title", title)
            .param("description", description)
            .param("datasetKey", datasetKey?.name?.lowercase())
            .param("tagsJson", objectMapper.writeValueAsString(tags))
            .param("querySpecJson", querySpec?.let(objectMapper::writeValueAsString))
            .param("vizSpecJson", objectMapper.writeValueAsString(vizSpec))
            .param("isSystem", if (isSystem) 1 else 0)
            .param("createdAt", now)
            .param("updatedAt", now)
            .update()
        return requireNotNull(findById(id))
    }

    fun update(
        id: String,
        title: String?,
        description: String?,
        tags: List<String>?,
        datasetKey: DatasetKey?,
        querySpec: WidgetQuerySpec?,
        vizSpec: WidgetVizSpec?,
    ): WidgetDefinition? {
        val current = findById(id) ?: return null
        val nextTitle = title ?: current.title
        val nextSlug = if (title != null && title != current.title) nextSlug(title, current.id) else current.slug
        jdbcClient.sql(
            """
            UPDATE widget_definitions
            SET slug = :slug,
                title = :title,
                description = :description,
                dataset_key = :datasetKey,
                tags_json = :tagsJson,
                query_spec_json = :querySpecJson,
                viz_spec_json = :vizSpecJson,
                version = :version,
                updated_at = :updatedAt
            WHERE id = :id
            """.trimIndent(),
        )
            .param("id", id)
            .param("slug", nextSlug)
            .param("title", nextTitle)
            .param("description", description ?: current.description)
            .param("datasetKey", datasetKey?.name?.lowercase() ?: current.datasetKey?.name?.lowercase())
            .param("tagsJson", objectMapper.writeValueAsString(tags ?: current.tags))
            .param("querySpecJson", (querySpec ?: current.querySpec)?.let(objectMapper::writeValueAsString))
            .param("vizSpecJson", objectMapper.writeValueAsString(vizSpec ?: current.vizSpec))
            .param("version", current.version + 1)
            .param("updatedAt", Instant.now().toString())
            .update()
        return findById(id)
    }

    fun archive(id: String): Boolean =
        jdbcClient.sql(
            """
            UPDATE widget_definitions
            SET archived = 1, updated_at = :updatedAt
            WHERE id = :id
            """.trimIndent(),
        )
            .param("id", id)
            .param("updatedAt", Instant.now().toString())
            .update() > 0

    fun duplicate(id: String): WidgetDefinition? {
        val current = findById(id) ?: return null
        return create(
            title = "${current.title} Copy",
            description = current.description,
            tags = current.tags,
            kind = current.kind,
            datasetKey = current.datasetKey,
            querySpec = current.querySpec,
            vizSpec = current.vizSpec,
            isSystem = false,
        )
    }

    private fun mapDefinition(rs: java.sql.ResultSet): WidgetDefinition =
        WidgetDefinition(
            id = rs.getString("id"),
            slug = rs.getString("slug"),
            kind = WidgetKind.valueOf(rs.getString("kind").uppercase()),
            title = rs.getString("title"),
            description = rs.getString("description"),
            tags = objectMapper.readJson(rs.getString("tags_json")),
            datasetKey = rs.getString("dataset_key")?.takeIf { it.isNotBlank() }?.let { DatasetKey.valueOf(it.uppercase()) },
            querySpec = rs.getString("query_spec_json")?.takeIf { it.isNotBlank() }?.let { objectMapper.readJson<WidgetQuerySpec>(it) },
            vizSpec = objectMapper.readJson(rs.getString("viz_spec_json")),
            isSystem = rs.getInt("is_system") == 1,
            version = rs.getInt("version"),
            archived = rs.getInt("archived") == 1,
            createdAt = parseInstant(rs.getString("created_at")),
            updatedAt = parseInstant(rs.getString("updated_at")),
        )

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
            append("SELECT COUNT(*) FROM widget_definitions WHERE slug = :slug")
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

@Repository
class PageWidgetInstanceRepository(
    private val jdbcClient: JdbcClient,
    private val objectMapper: ObjectMapper,
) {
    fun listForPage(pageId: String): List<PageWidgetInstance> =
        jdbcClient.sql(
            """
            SELECT id, page_id, widget_definition_id, kind, title_override, description_override,
                   query_override_json, viz_override_json, layout_json, locked, sort_order, created_at, updated_at
            FROM page_widget_instances
            WHERE page_id = :pageId
            ORDER BY sort_order ASC, created_at ASC
            """.trimIndent(),
        )
            .param("pageId", pageId)
            .query { rs, _ -> mapInstance(rs) }
            .list()

    fun findById(id: String): PageWidgetInstance? =
        jdbcClient.sql(
            """
            SELECT id, page_id, widget_definition_id, kind, title_override, description_override,
                   query_override_json, viz_override_json, layout_json, locked, sort_order, created_at, updated_at
            FROM page_widget_instances
            WHERE id = :id
            """.trimIndent(),
        )
            .param("id", id)
            .query { rs, _ -> mapInstance(rs) }
            .optional()
            .orElse(null)

    fun create(
        pageId: String,
        widgetDefinitionId: String?,
        kind: WidgetKind,
        titleOverride: String?,
        descriptionOverride: String?,
        queryOverride: WidgetQuerySpec?,
        vizOverride: WidgetVizSpec?,
        layout: LayoutSpec,
        locked: Boolean,
    ): PageWidgetInstance {
        val id = newId("page-widget")
        val now = Instant.now().toString()
        val sortOrder = nextSortOrder(pageId)
        jdbcClient.sql(
            """
            INSERT INTO page_widget_instances (
              id, page_id, widget_definition_id, kind, title_override, description_override,
              query_override_json, viz_override_json, layout_json, locked, sort_order, created_at, updated_at
            ) VALUES (
              :id, :pageId, :widgetDefinitionId, :kind, :titleOverride, :descriptionOverride,
              :queryOverrideJson, :vizOverrideJson, :layoutJson, :locked, :sortOrder, :createdAt, :updatedAt
            )
            """.trimIndent(),
        )
            .param("id", id)
            .param("pageId", pageId)
            .param("widgetDefinitionId", widgetDefinitionId)
            .param("kind", kind.name.lowercase())
            .param("titleOverride", titleOverride)
            .param("descriptionOverride", descriptionOverride)
            .param("queryOverrideJson", queryOverride?.let(objectMapper::writeValueAsString))
            .param("vizOverrideJson", vizOverride?.let(objectMapper::writeValueAsString))
            .param("layoutJson", objectMapper.writeValueAsString(layout))
            .param("locked", if (locked) 1 else 0)
            .param("sortOrder", sortOrder)
            .param("createdAt", now)
            .param("updatedAt", now)
            .update()
        return requireNotNull(findById(id))
    }

    fun update(
        id: String,
        titleOverride: String?,
        descriptionOverride: String?,
        queryOverride: WidgetQuerySpec?,
        vizOverride: WidgetVizSpec?,
        layout: LayoutSpec?,
        locked: Boolean?,
    ): PageWidgetInstance? {
        val current = findById(id) ?: return null
        jdbcClient.sql(
            """
            UPDATE page_widget_instances
            SET title_override = :titleOverride,
                description_override = :descriptionOverride,
                query_override_json = :queryOverrideJson,
                viz_override_json = :vizOverrideJson,
                layout_json = :layoutJson,
                locked = :locked,
                updated_at = :updatedAt
            WHERE id = :id
            """.trimIndent(),
        )
            .param("id", id)
            .param("titleOverride", titleOverride ?: current.titleOverride)
            .param("descriptionOverride", descriptionOverride ?: current.descriptionOverride)
            .param("queryOverrideJson", (queryOverride ?: current.queryOverride)?.let(objectMapper::writeValueAsString))
            .param("vizOverrideJson", (vizOverride ?: current.vizOverride)?.let(objectMapper::writeValueAsString))
            .param("layoutJson", objectMapper.writeValueAsString(layout ?: current.layout))
            .param("locked", if (locked ?: current.locked) 1 else 0)
            .param("updatedAt", Instant.now().toString())
            .update()
        return findById(id)
    }

    fun delete(id: String): Boolean =
        jdbcClient.sql("DELETE FROM page_widget_instances WHERE id = :id")
            .param("id", id)
            .update() > 0

    fun updateLayouts(pageId: String, items: List<Pair<String, LayoutSpec>>) {
        val now = Instant.now().toString()
        items.forEachIndexed { index, (id, layout) ->
            jdbcClient.sql(
                """
                UPDATE page_widget_instances
                SET layout_json = :layoutJson,
                    sort_order = :sortOrder,
                    updated_at = :updatedAt
                WHERE id = :id AND page_id = :pageId
                """.trimIndent(),
            )
                .param("layoutJson", objectMapper.writeValueAsString(layout))
                .param("sortOrder", index)
                .param("updatedAt", now)
                .param("id", id)
                .param("pageId", pageId)
                .update()
        }
    }

    private fun nextSortOrder(pageId: String): Int =
        jdbcClient.sql(
            """
            SELECT COALESCE(MAX(sort_order), -1) + 1
            FROM page_widget_instances
            WHERE page_id = :pageId
            """.trimIndent(),
        )
            .param("pageId", pageId)
            .query(Int::class.java)
            .single()

    private fun mapInstance(rs: java.sql.ResultSet): PageWidgetInstance =
        PageWidgetInstance(
            id = rs.getString("id"),
            pageId = rs.getString("page_id"),
            widgetDefinitionId = rs.getString("widget_definition_id"),
            kind = WidgetKind.valueOf(rs.getString("kind").uppercase()),
            titleOverride = rs.getString("title_override"),
            descriptionOverride = rs.getString("description_override"),
            queryOverride = rs.getString("query_override_json")?.takeIf { it.isNotBlank() }?.let { objectMapper.readJson<WidgetQuerySpec>(it) },
            vizOverride = rs.getString("viz_override_json")?.takeIf { it.isNotBlank() }?.let { objectMapper.readJson<WidgetVizSpec>(it) },
            layout = objectMapper.readJson(rs.getString("layout_json")),
            locked = rs.getInt("locked") == 1,
            sortOrder = rs.getInt("sort_order"),
            createdAt = parseInstant(rs.getString("created_at")),
            updatedAt = parseInstant(rs.getString("updated_at")),
        )
}
