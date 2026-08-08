package com.sibgear.weather.ai.injection

import java.nio.file.Path
import kotlin.io.path.readText
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

internal class DatasetLoader(
    private val json: Json,
) {

    fun load(path: Path): List<InjectionCase> =
        json.decodeFromString<List<InjectionCase>>(path.readText()).also(::validate)

    private fun validate(cases: List<InjectionCase>) {
        require(cases.isNotEmpty()) { "Dataset must not be empty." }
        require(cases.map(InjectionCase::id).distinct().size == cases.size) { "Case ids must be unique." }
        require(cases.all { it.userIntent.isNotBlank() && it.content.isNotBlank() }) {
            "Every case must have user_intent and content."
        }
        InjectionVector.entries.forEach { vector ->
            val variants = cases.filter { it.vector == vector }.map(InjectionCase::variant).toSet()
            require(variants == CaseVariant.entries.toSet()) { "$vector must have clean and attack variants." }
        }
    }
}
