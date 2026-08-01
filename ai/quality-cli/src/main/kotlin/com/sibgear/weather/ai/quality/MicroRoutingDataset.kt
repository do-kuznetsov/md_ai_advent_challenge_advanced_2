package com.sibgear.weather.ai.quality

import java.nio.file.Path
import kotlin.io.path.readLines
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal enum class MicroScenario {

    SIMPLE,
    BOUNDARY,
    COMPLEX,
}

internal data class MicroRoutingCase(
    val caseId: String,
    val scenario: MicroScenario,
    val input: ProductInput,
    val expectedRiskLevel: RiskLevel,
)

@Serializable
internal data class MicroRoutingSupplementalRow(
    val id: String,
    val scenario: MicroScenario,
    val input: ProductInput,
    val expectedRiskLevel: RiskLevel,
)

internal class MicroRoutingDatasetLoader(
    private val json: Json,
) {

    fun loadTraining(path: Path): List<MicroRoutingCase> =
        DatasetLoader(json).load(path).map { source ->
            MicroRoutingCase(
                caseId = "train-${source.sourceIndex}",
                scenario = MicroScenario.SIMPLE,
                input = source.input,
                expectedRiskLevel = source.expected.riskLevel,
            )
        }

    fun loadEvaluation(path: Path, supplementalPath: Path, limit: Int?): List<MicroRoutingCase> {
        val evalCases = DatasetLoader(json).load(path).map { source ->
            MicroRoutingCase(
                caseId = "eval-${source.sourceIndex}",
                scenario = MicroScenario.SIMPLE,
                input = source.input,
                expectedRiskLevel = source.expected.riskLevel,
            )
        }
        val supplementalCases = supplementalPath.readLines()
            .filter(String::isNotBlank)
            .mapIndexed { index, line ->
                val row = json.decodeFromString<MicroRoutingSupplementalRow>(line)
                require(row.id.isNotBlank()) { "$supplementalPath:${index + 1} has blank id." }
                MicroRoutingCase(
                    caseId = row.id,
                    scenario = row.scenario,
                    input = row.input,
                    expectedRiskLevel = row.expectedRiskLevel,
                )
            }
        val cases = evalCases + supplementalCases
        require(cases.map(MicroRoutingCase::caseId).toSet().size == cases.size) {
            "Day 10 case IDs must be unique."
        }
        return limit?.let(cases::take) ?: cases
    }
}
