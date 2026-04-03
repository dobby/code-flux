package com.company.throughput.annotations

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank
import java.time.LocalDate

enum class AnnotationType {
    @JsonProperty("adoption")
    ADOPTION,

    @JsonProperty("process_change")
    PROCESS_CHANGE,

    @JsonProperty("release")
    RELEASE,

    @JsonProperty("incident")
    INCIDENT,

    @JsonProperty("milestone")
    MILESTONE,

    @JsonProperty("custom")
    CUSTOM,
}

data class AnnotationDto(
    val annotationId: Long,
    val day: LocalDate,
    val title: String,
    val description: String?,
    val type: AnnotationType,
    val colorToken: String?,
)

data class CreateAnnotationRequest(
    val day: LocalDate,
    @field:NotBlank
    val title: String,
    val description: String? = null,
    val type: AnnotationType,
    val colorToken: String? = null,
)

data class UpdateAnnotationRequest(
    @field:NotBlank
    val title: String,
    val description: String? = null,
    val type: AnnotationType,
    val colorToken: String? = null,
)
