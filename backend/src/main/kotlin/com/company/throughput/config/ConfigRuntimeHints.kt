package com.company.throughput.config

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar

class ConfigRuntimeHints : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        val memberCategories = arrayOf(
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_METHODS,
            MemberCategory.INVOKE_PUBLIC_METHODS,
            MemberCategory.DECLARED_FIELDS,
        )

        listOf(
            EditableDashboardConfig::class.java,
            AppProperties::class.java,
            GitProperties::class.java,
            GitAuthProperties::class.java,
            AuthorsProperties::class.java,
            AuthorConfig::class.java,
            RepoConfig::class.java,
            ClassificationProperties::class.java,
            ClassificationRuleConfig::class.java,
            UiDefaultsProperties::class.java,
            SyncWindowProperties::class.java,
        ).forEach { type ->
            hints.reflection().registerType(type, *memberCategories)
        }
    }
}
