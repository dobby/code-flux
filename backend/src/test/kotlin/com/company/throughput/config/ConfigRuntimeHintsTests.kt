package com.company.throughput.config

import com.company.throughput.v2.model.CreateWidgetDefinitionRequest
import com.company.throughput.v2.model.WidgetVizSpec
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates

class ConfigRuntimeHintsTests {
    private val runtimeHints = RuntimeHints().also { ConfigRuntimeHints().registerHints(it, javaClass.classLoader) }

    @Test
    fun `registers widget models for reflection in native image`() {
        assertTrue(RuntimeHintsPredicates.reflection().onType(WidgetVizSpec::class.java).test(runtimeHints))
        assertTrue(RuntimeHintsPredicates.reflection().onType(CreateWidgetDefinitionRequest::class.java).test(runtimeHints))
    }

    @Test
    fun `registers kotlin empty collection implementations used by default values`() {
        assertTrue(RuntimeHintsPredicates.reflection().onType(emptyList<Any>()::class.java).test(runtimeHints))
        assertTrue(RuntimeHintsPredicates.reflection().onType(emptyMap<Any, Any>()::class.java).test(runtimeHints))
        assertTrue(RuntimeHintsPredicates.reflection().onType(emptySet<Any>()::class.java).test(runtimeHints))
    }
}
