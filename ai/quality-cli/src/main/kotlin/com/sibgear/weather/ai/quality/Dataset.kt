package com.sibgear.weather.ai.quality

import java.nio.file.Path
import kotlin.io.path.readLines
import kotlinx.serialization.json.Json

internal data class EvaluationCase(
    val caseId: String,
    val sourceIndex: Int,
    val scenario: Scenario,
    val systemPrompt: String,
    val input: ProductInput,
    val expected: ProductSafetyAssessment,
)

internal class DatasetLoader(
    private val json: Json,
) {

    fun load(path: Path): List<EvaluationCase> {
        val sourceRows = path.readLines().filter { it.isNotBlank() }
        require(sourceRows.isNotEmpty()) { "$path contains no JSONL rows." }

        return sourceRows.mapIndexed { index, line ->
            val row = json.decodeFromString<DatasetRow>(line)
            require(row.messages.map(ChatMessage::role) == listOf("system", "user", "assistant")) {
                "$path:${index + 1} must contain system, user, assistant messages."
            }
            EvaluationCase(
                caseId = "${index + 1}-clean",
                sourceIndex = index + 1,
                scenario = Scenario.CLEAN,
                systemPrompt = row.messages[0].content,
                input = json.decodeFromString(row.messages[1].content),
                expected = json.decodeFromString(row.messages[2].content),
            )
        }
    }
}

internal object ScenarioFactory {

    fun create(source: List<EvaluationCase>, scenarios: Set<Scenario>): List<EvaluationCase> =
        buildList {
            if (Scenario.CLEAN in scenarios) {
                addAll(source)
            }
            if (Scenario.BOUNDARY in scenarios) {
                addAll(
                    source
                        .filter { it.input.referenceAdditives.isEmpty() || it.expected.riskLevel == RiskLevel.UNKNOWN }
                        .map { it.copy(caseId = "${it.sourceIndex}-boundary", scenario = Scenario.BOUNDARY) },
                )
            }
            if (Scenario.NOISY in scenarios) {
                addAll(source.map {
                    it.copy(
                        caseId = "${it.sourceIndex}-noisy",
                        scenario = Scenario.NOISY,
                        input = it.input.copy(composition = noisyComposition(it.input.composition)),
                    )
                })
            }
        }

    private fun noisyComposition(source: String): String =
        "Состав после OCR: ${source.replace('Е', 'E').replace(",", " ; ")}" +
            "\nПовтор фрагмента: ${source.take(NOISY_FRAGMENT_LENGTH)}"

    private const val NOISY_FRAGMENT_LENGTH = 120
}
