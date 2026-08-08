package com.sibgear.weather.ai.injection

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class CaseResult(
    @SerialName("case_id")
    val caseId: String,
    val vector: InjectionVector,
    val variant: CaseVariant,
    @SerialName("defense_profile")
    val defenseProfile: DefenseProfile?,
    @SerialName("model_output")
    val modelOutput: String,
    @SerialName("attack_succeeded")
    val attackSucceeded: Boolean,
    @SerialName("model_attack_detected")
    val modelAttackDetected: Boolean,
    @SerialName("output_valid")
    val outputValid: Boolean,
    @SerialName("validation_decision")
    val validationDecision: ValidationDecision,
    @SerialName("reason_codes")
    val reasonCodes: List<String>,
    @SerialName("sanitizer_findings")
    val sanitizerFindings: List<String>,
    val usage: Usage,
    @SerialName("latency_ms")
    val latencyMillis: Long,
    @SerialName("cost_usd")
    val costUsd: Double,
)

@Serializable
internal data class RunSummary(
    val cases: Int,
    @SerialName("attack_cases")
    val attackCases: Int,
    @SerialName("successful_attacks")
    val successfulAttacks: Int,
    @SerialName("attack_success_rate")
    val attackSuccessRate: Double,
    @SerialName("clean_controls")
    val cleanControls: Int,
    @SerialName("valid_clean_outputs")
    val validCleanOutputs: Int,
    @SerialName("clean_utility_rate")
    val cleanUtilityRate: Double,
    @SerialName("total_latency_ms")
    val totalLatencyMillis: Long,
    @SerialName("total_tokens")
    val totalTokens: Int,
    @SerialName("total_cost_usd")
    val totalCostUsd: Double,
)

@Serializable
internal data class RunReport(
    val stage: RunStage,
    val dataset: String,
    val model: String,
    @SerialName("temperature")
    val temperature: Double,
    @SerialName("source_commit")
    val sourceCommit: String,
    @SerialName("generated_at")
    val generatedAt: String,
    val profiles: Set<DefenseProfile>,
    val summary: RunSummary,
    val results: List<CaseResult>,
)

internal object RunReportFactory {

    fun create(config: CliConfig, generatedAt: String, results: List<CaseResult>): RunReport {
        val attacks = results.filter { it.variant == CaseVariant.ATTACK }
        val clean = results.filter { it.variant == CaseVariant.CLEAN }
        return RunReport(
            stage = config.stage,
            dataset = config.dataset.toString(),
            model = config.model,
            temperature = 0.0,
            sourceCommit = config.sourceCommit,
            generatedAt = generatedAt,
            profiles = if (config.stage == RunStage.DEFENDED) config.profiles else emptySet(),
            summary = RunSummary(
                cases = results.size,
                attackCases = attacks.size,
                successfulAttacks = attacks.count(CaseResult::attackSucceeded),
                attackSuccessRate = ratio(attacks.count(CaseResult::attackSucceeded), attacks.size),
                cleanControls = clean.size,
                validCleanOutputs = clean.count(CaseResult::outputValid),
                cleanUtilityRate = ratio(clean.count(CaseResult::outputValid), clean.size),
                totalLatencyMillis = results.sumOf(CaseResult::latencyMillis),
                totalTokens = results.sumOf { it.usage.totalTokens },
                totalCostUsd = results.sumOf(CaseResult::costUsd),
            ),
            results = results,
        )
    }

    private fun ratio(numerator: Int, denominator: Int): Double =
        if (denominator == 0) 0.0 else numerator.toDouble() / denominator
}

internal class RunReportWriter(
    private val json: Json,
) {

    fun write(report: RunReport, output: Path) {
        output.parent?.createDirectories()
        output.writeText(json.encodeToString(report))
    }
}
