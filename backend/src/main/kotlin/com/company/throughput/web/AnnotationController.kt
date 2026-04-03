package com.company.throughput.web

import com.company.throughput.annotations.AnnotationDto
import com.company.throughput.annotations.AnnotationService
import com.company.throughput.annotations.CreateAnnotationRequest
import com.company.throughput.annotations.UpdateAnnotationRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/annotations")
class AnnotationController(
    private val annotationService: AnnotationService,
) {
    @GetMapping
    fun list(
        @RequestParam(required = false) from: LocalDate?,
        @RequestParam(required = false) to: LocalDate?,
    ): List<AnnotationDto> = annotationService.list(from, to)

    @PostMapping
    fun create(@Valid @RequestBody request: CreateAnnotationRequest): AnnotationDto =
        annotationService.create(request)

    @PutMapping("/{annotationId}")
    fun update(
        @PathVariable annotationId: Long,
        @Valid @RequestBody request: UpdateAnnotationRequest,
    ): AnnotationDto = annotationService.update(annotationId, request)

    @DeleteMapping("/{annotationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable annotationId: Long) {
        annotationService.delete(annotationId)
    }
}
