package com.company.throughput.v2.repo

import com.company.throughput.v2.model.AnnotationCommitRef
import com.company.throughput.v2.model.AnnotationTargetKind
import com.company.throughput.v2.model.AnnotationTypeV2
import com.company.throughput.v2.model.AnnotationV2
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate

@Repository
class AnnotationV2Repository(
    private val jdbcClient: JdbcClient,
    private val objectMapper: ObjectMapper,
) {
    fun list(
        pageWidgetInstanceId: String? = null,
        dateFrom: LocalDate? = null,
        dateTo: LocalDate? = null,
    ): List<AnnotationV2> {
        val clauses = mutableListOf("1 = 1")
        var spec = jdbcClient.sql(
            """
            SELECT id, target_kind, page_widget_instance_id, scope_date, x_value, y_value,
                   annotation_type, name, description, title, body, color, tags_json, commit_refs_json,
                   scope_json, created_at, updated_at
            FROM annotations_v2
            WHERE 1 = 1
            """.trimIndent(),
        )
        if (pageWidgetInstanceId != null) {
            clauses += "page_widget_instance_id = :pageWidgetInstanceId"
            spec = spec.param("pageWidgetInstanceId", pageWidgetInstanceId)
        }
        if (dateFrom != null) {
            clauses += "scope_date >= :dateFrom"
            spec = spec.param("dateFrom", dateFrom.toString())
        }
        if (dateTo != null) {
            clauses += "scope_date <= :dateTo"
            spec = spec.param("dateTo", dateTo.toString())
        }
        val sql = buildString {
            append(
                """
                SELECT id, target_kind, page_widget_instance_id, scope_date, x_value, y_value,
                       annotation_type, name, description, title, body, color, tags_json, commit_refs_json,
                       scope_json, created_at, updated_at
                FROM annotations_v2
                WHERE ${clauses.joinToString(" AND ")}
                ORDER BY scope_date ASC, created_at ASC
                """.trimIndent(),
            )
        }
        spec = jdbcClient.sql(sql)
        if (pageWidgetInstanceId != null) {
            spec = spec.param("pageWidgetInstanceId", pageWidgetInstanceId)
        }
        if (dateFrom != null) {
            spec = spec.param("dateFrom", dateFrom.toString())
        }
        if (dateTo != null) {
            spec = spec.param("dateTo", dateTo.toString())
        }
        return spec.query { rs, _ -> mapAnnotation(rs) }.list()
    }

    fun findById(id: String): AnnotationV2? =
        jdbcClient.sql(
            """
            SELECT id, target_kind, page_widget_instance_id, scope_date, x_value, y_value,
                   annotation_type, name, description, title, body, color, tags_json, commit_refs_json,
                   scope_json, created_at, updated_at
            FROM annotations_v2
            WHERE id = :id
            """.trimIndent(),
        )
            .param("id", id)
            .query { rs, _ -> mapAnnotation(rs) }
            .optional()
            .orElse(null)

    fun create(annotation: AnnotationV2): AnnotationV2 {
        jdbcClient.sql(
            """
            INSERT INTO annotations_v2 (
              id, target_kind, page_widget_instance_id, scope_date, x_value, y_value,
              annotation_type, name, description, title, body, color, tags_json, commit_refs_json,
              scope_json, created_at, updated_at
            ) VALUES (
              :id, :targetKind, :pageWidgetInstanceId, :scopeDate, :xValue, :yValue,
              :annotationType, :name, :description, :title, :body, :color, :tagsJson, :commitRefsJson,
              :scopeJson, :createdAt, :updatedAt
            )
            """.trimIndent(),
        )
            .param("id", annotation.id)
            .param("targetKind", annotation.targetKind.name.lowercase())
            .param("pageWidgetInstanceId", annotation.pageWidgetInstanceId)
            .param("scopeDate", annotation.scopeDate?.toString())
            .param("xValue", annotation.xValue)
            .param("yValue", annotation.yValue)
            .param("annotationType", annotation.annotationType.name.lowercase())
            .param("name", annotation.name)
            .param("description", annotation.description)
            .param("title", annotation.title)
            .param("body", annotation.body)
            .param("color", annotation.color)
            .param("tagsJson", objectMapper.writeValueAsString(annotation.tags))
            .param("commitRefsJson", objectMapper.writeValueAsString(annotation.commitRefs))
            .param("scopeJson", objectMapper.writeValueAsString(annotation.scope))
            .param("createdAt", annotation.createdAt.toString())
            .param("updatedAt", annotation.updatedAt.toString())
            .update()
        return requireNotNull(findById(annotation.id))
    }

    fun update(
        id: String,
        annotationType: AnnotationTypeV2?,
        name: String?,
        description: String?,
        title: String?,
        body: String?,
        color: String?,
        tags: List<String>?,
        commitRefs: List<AnnotationCommitRef>?,
    ): AnnotationV2? {
        val current = findById(id) ?: return null
        val now = Instant.now()
        val effectiveName = name ?: title ?: current.name
        val effectiveTitle = title ?: name ?: current.title
        val effectiveDescription = description ?: body ?: current.description
        val effectiveBody = body ?: description ?: current.body
        jdbcClient.sql(
            """
            UPDATE annotations_v2
            SET annotation_type = :annotationType,
                name = :name,
                description = :description,
                title = :title,
                body = :body,
                color = :color,
                tags_json = :tagsJson,
                commit_refs_json = :commitRefsJson,
                updated_at = :updatedAt
            WHERE id = :id
            """.trimIndent(),
        )
            .param("id", id)
            .param("annotationType", annotationType?.name?.lowercase() ?: current.annotationType.name.lowercase())
            .param("name", effectiveName)
            .param("description", effectiveDescription)
            .param("title", effectiveTitle)
            .param("body", effectiveBody)
            .param("color", color ?: current.color)
            .param("tagsJson", objectMapper.writeValueAsString(tags ?: current.tags))
            .param("commitRefsJson", objectMapper.writeValueAsString(commitRefs ?: current.commitRefs))
            .param("updatedAt", now.toString())
            .update()
        return current.copy(
            annotationType = annotationType ?: current.annotationType,
            name = effectiveName,
            description = effectiveDescription,
            title = effectiveTitle,
            body = effectiveBody,
            color = color ?: current.color,
            tags = tags ?: current.tags,
            commitRefs = commitRefs ?: current.commitRefs,
            updatedAt = now,
        )
    }

    fun delete(id: String): Boolean =
        jdbcClient.sql("DELETE FROM annotations_v2 WHERE id = :id")
            .param("id", id)
            .update() > 0

    private fun mapAnnotation(rs: java.sql.ResultSet): AnnotationV2 =
        AnnotationV2(
            id = rs.getString("id"),
            targetKind = AnnotationTargetKind.valueOf(rs.getString("target_kind").uppercase()),
            pageWidgetInstanceId = rs.getString("page_widget_instance_id"),
            scopeDate = parseLocalDate(rs.getString("scope_date")),
            xValue = rs.getString("x_value"),
            yValue = rs.getDouble("y_value").takeUnless { rs.wasNull() },
            annotationType = AnnotationTypeV2.valueOf(rs.getString("annotation_type").uppercase()),
            name = rs.getString("name"),
            description = rs.getString("description"),
            title = rs.getString("title"),
            body = rs.getString("body"),
            color = rs.getString("color"),
            tags = objectMapper.readJson(rs.getString("tags_json")),
            commitRefs = objectMapper.readJson(rs.getString("commit_refs_json")),
            scope = objectMapper.readMap(rs.getString("scope_json")),
            createdAt = parseInstant(rs.getString("created_at")),
            updatedAt = parseInstant(rs.getString("updated_at")),
        )
}
