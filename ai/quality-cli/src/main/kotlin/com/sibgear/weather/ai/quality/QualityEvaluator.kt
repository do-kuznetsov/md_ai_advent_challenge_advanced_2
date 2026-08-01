package com.sibgear.weather.ai.quality

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class CallStats(
    val latencyMillis: Long,
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
    val costUsd: Double,
)

@Serializable
internal data class AttemptReport(
    val index: Int,
    val primary: CallStats? = null,
    val selfCheck: CallStats? = null,
    val candidate: AssessmentEnvelope? = null,
    val selfCheckResult: SelfCheckResult? = null,
    val reasons: List<String>,
)

@Serializable
internal data class CaseReport(
    val caseId: String,
    val sourceIndex: Int,
    val scenario: Scenario,
    val input: ProductInput,
    val expected: ProductSafetyAssessment,
    val accepted: Boolean,
    val finalCandidate: AssessmentEnvelope? = null,
    val reasons: List<String>,
    val attempts: List<AttemptReport>,
    val riskLevelMatchesExpected: Boolean? = null,
    val additiveCodesMatchExpected: Boolean? = null,
)

internal class QualityEvaluator(
    private val config: CliConfig,
    private val gateway: DeepSeekGateway,
    private val json: Json,
) {

    suspend fun evaluate(cases: List<EvaluationCase>): List<CaseReport> = buildList {
        cases.forEach { evaluationCase -> add(evaluateCase(evaluationCase)) }
    }

    @Suppress(
        "LongMethod",
        "CyclomaticComplexMethod",
        "NestedBlockDepth",
        "LoopWithTooManyJumpStatements",
    )
    private suspend fun evaluateCase(evaluationCase: EvaluationCase): CaseReport {
        val attempts = mutableListOf<AttemptReport>()
        var finalCandidate: AssessmentEnvelope? = null
        var finalReasons = listOf("No inference attempt was made")
        var accepted = false

        for (attemptIndex in 1..config.maxAttempts) {
            val reasons = mutableListOf<String>()
            val primaryResult = gateway.complete(primaryMessages(evaluationCase))
            if (primaryResult is GatewayResult.Failure) {
                reasons += "Primary request failed: ${primaryResult.message}"
                attempts += AttemptReport(index = attemptIndex, reasons = reasons)
                finalReasons = reasons
                break
            }
            primaryResult as GatewayResult.Success
            val primaryStats = callStats(primaryResult.response)
            val candidate = runCatching {
                json.decodeFromString<AssessmentEnvelope>(primaryResult.response.content)
            }.getOrElse { error ->
                reasons += "Primary response is not a valid envelope: ${error.message}"
                attempts += AttemptReport(index = attemptIndex, primary = primaryStats, reasons = reasons)
                finalReasons = reasons
                continue
            }
            finalCandidate = candidate

            if (CheckType.CONSTRAINTS in config.checks) {
                reasons += QualityConstraints.validate(candidate, evaluationCase.input)
                if (reasons.isNotEmpty()) {
                    attempts += AttemptReport(
                        index = attemptIndex,
                        primary = primaryStats,
                        candidate = candidate,
                        reasons = reasons,
                    )
                    finalReasons = reasons
                    continue
                }
            }

            if (CheckType.SCORING in config.checks) {
                when (candidate.status) {
                    DecisionStatus.FAIL -> {
                        reasons += "Model scoring status is FAIL"
                        attempts += AttemptReport(
                            index = attemptIndex,
                            primary = primaryStats,
                            candidate = candidate,
                            reasons = reasons,
                        )
                        finalReasons = reasons
                        break
                    }

                    DecisionStatus.UNSURE -> {
                        reasons += "Model scoring status is UNSURE"
                        attempts += AttemptReport(
                            index = attemptIndex,
                            primary = primaryStats,
                            candidate = candidate,
                            reasons = reasons,
                        )
                        finalReasons = reasons
                        continue
                    }

                    DecisionStatus.OK -> Unit
                }
                if (candidate.confidenceScore < config.confidenceThreshold) {
                    reasons += "confidence_score is below threshold ${config.confidenceThreshold}"
                    attempts += AttemptReport(
                        index = attemptIndex,
                        primary = primaryStats,
                        candidate = candidate,
                        reasons = reasons,
                    )
                    finalReasons = reasons
                    continue
                }
            }

            if (CheckType.SELF_CHECK in config.checks) {
                val selfCheckGatewayResult = gateway.complete(selfCheckMessages(evaluationCase.input, candidate))
                if (selfCheckGatewayResult is GatewayResult.Failure) {
                    reasons += "Self-check request failed: ${selfCheckGatewayResult.message}"
                    attempts += AttemptReport(
                        index = attemptIndex,
                        primary = primaryStats,
                        candidate = candidate,
                        reasons = reasons,
                    )
                    finalReasons = reasons
                    break
                }
                selfCheckGatewayResult as GatewayResult.Success
                val selfCheckStats = callStats(selfCheckGatewayResult.response)
                val selfCheck = runCatching {
                    json.decodeFromString<SelfCheckResult>(selfCheckGatewayResult.response.content)
                }.getOrElse { error ->
                    reasons += "Self-check response is invalid: ${error.message}"
                    attempts += AttemptReport(
                        index = attemptIndex,
                        primary = primaryStats,
                        selfCheck = selfCheckStats,
                        candidate = candidate,
                        reasons = reasons,
                    )
                    finalReasons = reasons
                    continue
                }
                reasons += selfCheck.issues.filter(String::isNotBlank)
                when (selfCheck.status) {
                    DecisionStatus.FAIL -> {
                        if (reasons.isEmpty()) {
                            reasons += "Self-check status is FAIL"
                        }
                        attempts += AttemptReport(
                            index = attemptIndex,
                            primary = primaryStats,
                            selfCheck = selfCheckStats,
                            candidate = candidate,
                            selfCheckResult = selfCheck,
                            reasons = reasons,
                        )
                        finalReasons = reasons
                        break
                    }

                    DecisionStatus.UNSURE -> {
                        if (reasons.isEmpty()) {
                            reasons += "Self-check status is UNSURE"
                        }
                        attempts += AttemptReport(
                            index = attemptIndex,
                            primary = primaryStats,
                            selfCheck = selfCheckStats,
                            candidate = candidate,
                            selfCheckResult = selfCheck,
                            reasons = reasons,
                        )
                        finalReasons = reasons
                        continue
                    }

                    DecisionStatus.OK -> Unit
                }
                attempts += AttemptReport(
                    index = attemptIndex,
                    primary = primaryStats,
                    selfCheck = selfCheckStats,
                    candidate = candidate,
                    selfCheckResult = selfCheck,
                    reasons = reasons,
                )
            } else {
                attempts += AttemptReport(
                    index = attemptIndex,
                    primary = primaryStats,
                    candidate = candidate,
                    reasons = reasons,
                )
            }
            accepted = true
            finalReasons = emptyList()
            break
        }

        return CaseReport(
            caseId = evaluationCase.caseId,
            sourceIndex = evaluationCase.sourceIndex,
            scenario = evaluationCase.scenario,
            input = evaluationCase.input,
            expected = evaluationCase.expected,
            accepted = accepted,
            finalCandidate = finalCandidate,
            reasons = finalReasons,
            attempts = attempts,
            riskLevelMatchesExpected = finalCandidate?.answer?.riskLevel == evaluationCase.expected.riskLevel,
            additiveCodesMatchExpected = finalCandidate?.answer?.matchedAdditives?.map { it.code }?.toSet() ==
                evaluationCase.expected.matchedAdditives.map { it.code }.toSet(),
        )
    }

    private fun primaryMessages(evaluationCase: EvaluationCase): List<ChatMessage> = listOf(
        ChatMessage(
            role = "system",
            content = """
                ${evaluationCase.systemPrompt}
                Верни JSON-envelope без Markdown: {"answer": ProductSafetyAssessment, "confidence_score": number 0..1, "status": "OK|UNSURE|FAIL"}.
                answer обязан иметь ключи risk_level, matched_additives, warnings, safe_summary, confidence.
                JSON обязателен.
            """.trimIndent(),
        ),
        ChatMessage(role = "user", content = json.encodeToString(evaluationCase.input)),
    )

    private fun selfCheckMessages(
        input: ProductInput,
        candidate: AssessmentEnvelope,
    ): List<ChatMessage> = listOf(
        ChatMessage(
            role = "system",
            content = "Проверь кандидат только по input.reference_additives. Верни JSON: " +
                "{\"status\":\"OK|UNSURE|FAIL\",\"issues\":[\"краткая причина\"]}. " +
                "Не объясняй внутренние рассуждения.",
        ),
        ChatMessage(
            role = "user",
            content = json.encodeToString(SelfCheckInput(input = input, candidate = candidate)),
        ),
    )

    private fun callStats(response: RemoteResponse): CallStats =
        CallStats(
            latencyMillis = response.latencyMillis,
            inputTokens = response.usage.promptTokens,
            outputTokens = response.usage.completionTokens,
            totalTokens = response.usage.totalTokens,
            costUsd = response.usage.promptTokens * config.inputPricePerMillion / MILLION +
                response.usage.completionTokens * config.outputPricePerMillion / MILLION,
        )

    private companion object {

        const val MILLION = 1_000_000.0
    }
}

@Serializable
private data class SelfCheckInput(
    val input: ProductInput,
    val candidate: AssessmentEnvelope,
)
