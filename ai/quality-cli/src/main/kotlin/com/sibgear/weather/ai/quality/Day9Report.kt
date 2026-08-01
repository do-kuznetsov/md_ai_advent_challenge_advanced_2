package com.sibgear.weather.ai.quality

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class Day9ReportConfig(
    val mode: CliMode,
    val dataset: String,
    val scenarios: Set<Scenario>,
    val model: String,
    val inputPricePerMillion: Double,
    val outputPricePerMillion: Double,
)

@Serializable
internal data class StageReport(
    val stage: InferenceStage,
    val stats: CallStats? = null,
    val succeeded: Boolean,
    val normalization: NormalizationResult? = null,
    val decision: RiskDecision? = null,
    val answer: ProductSafetyAssessment? = null,
    val reasons: List<String>,
)

@Serializable
internal data class Day9CaseReport(
    val caseId: String,
    val sourceIndex: Int,
    val scenario: Scenario,
    val input: ProductInput,
    val expected: ProductSafetyAssessment,
    val accepted: Boolean,
    val finalAnswer: ProductSafetyAssessment? = null,
    val reasons: List<String>,
    val stages: List<StageReport>,
    val riskLevelMatchesExpected: Boolean? = null,
    val additiveCodesMatchExpected: Boolean? = null,
)

@Serializable
internal data class Day9Summary(
    val cases: Int,
    val accepted: Int,
    val rejected: Int,
    val totalLatencyMillis: Long,
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
    val totalCostUsd: Double,
    val riskLevelMatchesExpected: Int,
    val additiveCodesMatchExpected: Int,
)

@Serializable
internal data class Day9StageSummary(
    val calls: Int,
    val succeeded: Int,
    val failed: Int,
    val latencyMillis: Long,
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
    val costUsd: Double,
)

@Serializable
internal data class Day9Report(
    val config: Day9ReportConfig,
    val overall: Day9Summary,
    val byScenario: Map<Scenario, Day9Summary>,
    val byStage: Map<InferenceStage, Day9StageSummary>,
    val results: List<Day9CaseReport>,
)

internal object Day9ReportFactory {

    fun create(config: CliConfig, results: List<Day9CaseReport>): Day9Report =
        Day9Report(
            config = Day9ReportConfig(
                mode = config.mode,
                dataset = config.dataset.toString(),
                scenarios = config.scenarios,
                model = config.model,
                inputPricePerMillion = config.inputPricePerMillion,
                outputPricePerMillion = config.outputPricePerMillion,
            ),
            overall = summarize(results),
            byScenario = results.groupBy(Day9CaseReport::scenario).mapValues { (_, cases) -> summarize(cases) },
            byStage = stagesFor(config.mode).associateWith { stage ->
                summarizeStage(results.flatMap(Day9CaseReport::stages).filter { it.stage == stage })
            },
            results = results,
        )

    private fun summarize(results: List<Day9CaseReport>): Day9Summary {
        val calls = results.flatMap(Day9CaseReport::stages).mapNotNull(StageReport::stats)
        return Day9Summary(
            cases = results.size,
            accepted = results.count(Day9CaseReport::accepted),
            rejected = results.count { !it.accepted },
            totalLatencyMillis = calls.sumOf(CallStats::latencyMillis),
            inputTokens = calls.sumOf(CallStats::inputTokens),
            outputTokens = calls.sumOf(CallStats::outputTokens),
            totalTokens = calls.sumOf(CallStats::totalTokens),
            totalCostUsd = calls.sumOf(CallStats::costUsd),
            riskLevelMatchesExpected = results.count { it.riskLevelMatchesExpected == true },
            additiveCodesMatchExpected = results.count { it.additiveCodesMatchExpected == true },
        )
    }

    private fun summarizeStage(stages: List<StageReport>): Day9StageSummary {
        val calls = stages.mapNotNull(StageReport::stats)
        return Day9StageSummary(
            calls = calls.size,
            succeeded = stages.count(StageReport::succeeded),
            failed = stages.count { !it.succeeded },
            latencyMillis = calls.sumOf(CallStats::latencyMillis),
            inputTokens = calls.sumOf(CallStats::inputTokens),
            outputTokens = calls.sumOf(CallStats::outputTokens),
            totalTokens = calls.sumOf(CallStats::totalTokens),
            costUsd = calls.sumOf(CallStats::costUsd),
        )
    }

    private fun stagesFor(mode: CliMode): List<InferenceStage> =
        when (mode) {
            CliMode.MONOLITHIC -> listOf(InferenceStage.MONOLITHIC)
            CliMode.MULTI_STAGE -> listOf(
                InferenceStage.NORMALIZATION,
                InferenceStage.DECISION,
                InferenceStage.RENDERING,
            )

            CliMode.QUALITY,
            CliMode.ROUTING,
            -> error("Day 9 report is not supported for ${mode.name}.")
        }
}

internal class Day9ReportWriter(
    private val json: Json,
) {

    fun write(report: Day9Report, output: Path) {
        output.parent?.createDirectories()
        output.writeText(json.encodeToString(report))
    }
}
