package com.company.throughput.v2.repo

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

internal fun newId(prefix: String): String = "$prefix-${UUID.randomUUID()}"

internal fun slugify(input: String): String {
    val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.getDefault())
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
    return normalized.ifBlank { "item" }
}

internal fun parseInstant(value: String): Instant = when {
    value.contains("T") -> Instant.parse(
        if (value.endsWith("Z")) value else "${value}Z",
    )

    else -> Instant.parse(value.replace(" ", "T") + "Z")
}

internal fun parseLocalDate(value: String?): LocalDate? = value?.takeIf { it.isNotBlank() }?.let(LocalDate::parse)

internal inline fun <reified T> ObjectMapper.readJson(value: String): T =
    readValue(value, object : TypeReference<T>() {})

internal fun ObjectMapper.readMap(value: String): Map<String, Any?> =
    readValue(value, object : TypeReference<Map<String, Any?>>() {})
