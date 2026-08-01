package com.sibgear.weather.ai.quality

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class ReportConfig(
    val dataset: String,
    val checks: Set<CheckType>,
    val scenarios: Set<Scenario>,
    val model: String,
    val confidenceThreshold: Double,
    val maxAttempts: Int,
    val inputPricePerMillion: Double,
    val outputPricePerMillion: Double,
)

@Serializable
internal data class QualitySummary(
    val cases: Int,
    val accepted: Int,
    val rejected: Int,
    val retried: Int,
    val primaryCalls: Int,
    val selfCheckCalls: Int,
    val totalLatencyMillis: Long,
    val additionalLatencyMillis: Long,
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
    val totalCostUsd: Double,
    val additionalCostUsd: Double,
    val riskLevelMatchesExpected: Int,
    val additiveCodesMatchExpected: Int,
)

@Serializable
internal data class QualityReport(
    val config: ReportConfig,
    val overall: QualitySummary,
    val byScenario: Map<Scenario, QualitySummary>,
    val results: List<CaseReport>,
)

internal object ReportFactory {

    fun create(config: CliConfig, results: List<CaseReport>): QualityReport =
        QualityReport(
            config = ReportConfig(
                dataset = config.dataset.toString(),
                checks = config.checks,
                scenarios = config.scenarios,
                model = config.model,
                confidenceThreshold = config.confidenceThreshold,
                maxAttempts = config.maxAttempts,
                inputPricePerMillion = config.inputPricePerMillion,
                outputPricePerMillion = config.outputPricePerMillion,
            ),
            overall = summarize(results),
            byScenario = results.groupBy(CaseReport::scenario).mapValues { (_, cases) -> summarize(cases) },
            results = results,
        )

    private fun summarize(results: List<CaseReport>): QualitySummary {
        val attempts = results.flatMap(CaseReport::attempts)
        val calls = attempts.flatMap { attempt -> listOfNotNull(attempt.primary, attempt.selfCheck) }
        val primaryCalls = attempts.mapNotNull(AttemptReport::primary)
        val baselineCalls = results.mapNotNull { result -> result.attempts.firstOrNull()?.primary }
        val baselineLatency = baselineCalls.sumOf(CallStats::latencyMillis)
        val baselineCost = baselineCalls.sumOf(CallStats::costUsd)

        return QualitySummary(
            cases = results.size,
            accepted = results.count(CaseReport::accepted),
            rejected = results.count { !it.accepted },
            retried = results.count { it.attempts.size > 1 },
            primaryCalls = primaryCalls.size,
            selfCheckCalls = attempts.count { it.selfCheck != null },
            totalLatencyMillis = calls.sumOf(CallStats::latencyMillis),
            additionalLatencyMillis = calls.sumOf(CallStats::latencyMillis) - baselineLatency,
            inputTokens = calls.sumOf(CallStats::inputTokens),
            outputTokens = calls.sumOf(CallStats::outputTokens),
            totalTokens = calls.sumOf(CallStats::totalTokens),
            totalCostUsd = calls.sumOf(CallStats::costUsd),
            additionalCostUsd = calls.sumOf(CallStats::costUsd) - baselineCost,
            riskLevelMatchesExpected = results.count { it.riskLevelMatchesExpected == true },
            additiveCodesMatchExpected = results.count { it.additiveCodesMatchExpected == true },
        )
    }
}

internal class ReportWriter(
    private val json: Json,
) {

    fun write(report: QualityReport, output: Path) {
        output.parent?.createDirectories()
        output.writeText(json.encodeToString(report))
    }
}
