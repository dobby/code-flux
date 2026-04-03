package com.company.throughput.annotations

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class AnnotationService(
    private val jdbcClient: JdbcClient,
) {
    fun list(from: LocalDate?, to: LocalDate?): List<AnnotationDto> {
        val clauses = mutableListOf<String>()
        val params = linkedMapOf<String, Any?>()

        if (from != null) {
            clauses += "day >= :from"
            params["from"] = from.toString()
        }
        if (to != null) {
            clauses += "day <= :to"
            params["to"] = to.toString()
        }

        val whereClause = if (clauses.isEmpty()) "" else "WHERE ${clauses.joinToString(" AND ")}"
        var spec = jdbcClient.sql(
            """
            SELECT annotation_id, day, title, description, type, color_token
            FROM annotation
            $whereClause
            ORDER BY day ASC, annotation_id ASC
            """.trimIndent(),
        )
        params.forEach { (key, value) -> spec = spec.param(key, value) }

        return spec.query { rs, _ ->
            AnnotationDto(
                annotationId = rs.getLong("annotation_id"),
                day = LocalDate.parse(rs.getString("day")),
                title = rs.getString("title"),
                description = rs.getString("description"),
                type = AnnotationType.valueOf(rs.getString("type")),
                colorToken = rs.getString("color_token"),
            )
        }.list()
    }

    @Transactional
    fun create(request: CreateAnnotationRequest): AnnotationDto {
        jdbcClient.sql(
            """
            INSERT INTO annotation (day, title, description, type, color_token, created_at, updated_at)
            VALUES (:day, :title, :description, :type, :colorToken, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
        )
            .param("day", request.day.toString())
            .param("title", request.title.trim())
            .param("description", request.description)
            .param("type", request.type.name)
            .param("colorToken", request.colorToken)
            .update()

        val annotationId = jdbcClient.sql("SELECT last_insert_rowid()")
            .query(Long::class.java)
            .single()

        return get(annotationId)
    }

    @Transactional
    fun update(annotationId: Long, request: UpdateAnnotationRequest): AnnotationDto {
        val updated = jdbcClient.sql(
            """
            UPDATE annotation
            SET title = :title,
                description = :description,
                type = :type,
                color_token = :colorToken,
                updated_at = CURRENT_TIMESTAMP
            WHERE annotation_id = :annotationId
            """.trimIndent(),
        )
            .param("annotationId", annotationId)
            .param("title", request.title.trim())
            .param("description", request.description)
            .param("type", request.type.name)
            .param("colorToken", request.colorToken)
            .update()

        require(updated > 0) { "Annotation $annotationId was not found" }
        return get(annotationId)
    }

    @Transactional
    fun delete(annotationId: Long) {
        val deleted = jdbcClient.sql("DELETE FROM annotation WHERE annotation_id = :annotationId")
            .param("annotationId", annotationId)
            .update()
        require(deleted > 0) { "Annotation $annotationId was not found" }
    }

    private fun get(annotationId: Long): AnnotationDto =
        jdbcClient.sql(
            """
            SELECT annotation_id, day, title, description, type, color_token
            FROM annotation
            WHERE annotation_id = :annotationId
            """.trimIndent(),
        )
            .param("annotationId", annotationId)
            .query { rs, _ ->
                AnnotationDto(
                    annotationId = rs.getLong("annotation_id"),
                    day = LocalDate.parse(rs.getString("day")),
                    title = rs.getString("title"),
                    description = rs.getString("description"),
                    type = AnnotationType.valueOf(rs.getString("type")),
                    colorToken = rs.getString("color_token"),
                )
            }
            .single()
}
