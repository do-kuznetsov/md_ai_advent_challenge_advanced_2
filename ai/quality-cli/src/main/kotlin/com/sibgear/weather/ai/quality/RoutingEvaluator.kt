package com.sibgear.weather.ai.quality

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class RoutingEvaluator(
    private val config: CliConfig,
    private val smallGateway: DeepSeekGateway,
    private val largeGateway: DeepSeekGateway,
    private val json: Json,
    private val progressReporter: ProgressReporter,
) {

    private val smallEndpoint: ModelEndpoint = ModelEndpoint(
        gateway = smallGateway,
        model = config.smallModel,
        role = ModelRole.SMALL,
        inputPricePerMillion = config.smallInputPricePerMillion,
        outputPricePerMillion = config.smallOutputPricePerMillion,
    )

    private val largeEndpoint: ModelEndpoint = ModelEndpoint(
        gateway = largeGateway,
        model = config.largeModel,
        role = ModelRole.LARGE,
        inputPricePerMillion = config.largeInputPricePerMillion,
        outputPricePerMillion = config.largeOutputPricePerMillion,
    )

    suspend fun evaluate(cases: List<EvaluationCase>): List<CaseReport> = buildList {
        progressReporter.onRunStarted(config = config, casesCount = cases.size)
        cases.forEach { evaluationCase -> add(evaluateCase(evaluationCase)) }
    }

    private suspend fun evaluateCase(evaluationCase: EvaluationCase): CaseReport {
        progressReporter.onCaseStage(caseId = evaluationCase.caseId, stage = "flash.start")
        val smallResult = evaluateModel(
            evaluationCase = evaluationCase,
            endpoint = smallEndpoint,
        )
        return when (smallResult.decision) {
            ModelDecision.ACCEPT -> acceptedCase(
                evaluationCase = evaluationCase,
                result = smallResult,
                routingDecision = RoutingDecision.ACCEPTED_ON_SMALL,
                attempts = listOf(
                    smallResult.toAttempt(
                        index = 1,
                        routingDecision = RoutingDecision.ACCEPTED_ON_SMALL,
                    ),
                ),
            ).also {
                progressReporter.onCaseStage(caseId = evaluationCase.caseId, stage = "flash.accept")
            }

            ModelDecision.REJECT -> rejectedCase(
                evaluationCase = evaluationCase,
                candidate = smallResult.candidate,
                reasons = smallResult.reasons,
                attempts = listOf(
                    smallResult.toAttempt(index = 1, routingDecision = RoutingDecision.REJECTED),
                ),
            ).also {
                progressReporter.onCaseStage(
                    caseId = evaluationCase.caseId,
                    stage = "reject",
                    reason = smallResult.reasons.compactReason(),
                )
            }

            ModelDecision.ESCALATE -> evaluateLargeModel(evaluationCase, smallResult)
        }
    }

    private suspend fun evaluateLargeModel(
        evaluationCase: EvaluationCase,
        smallResult: ModelEvaluation,
    ): CaseReport {
        progressReporter.onCaseStage(
            caseId = evaluationCase.caseId,
            stage = "escalate",
            reason = smallResult.reasons.compactReason(),
        )
        progressReporter.onCaseStage(caseId = evaluationCase.caseId, stage = "pro.start")
        val largeResult = evaluateModel(
            evaluationCase = evaluationCase,
            endpoint = largeEndpoint,
        )
        val attempts = listOf(
            smallResult.toAttempt(index = 1, routingDecision = RoutingDecision.ESCALATED_TO_LARGE),
            largeResult.toAttempt(
                index = 2,
                routingDecision = if (largeResult.decision == ModelDecision.ACCEPT) {
                    RoutingDecision.ACCEPTED_ON_LARGE
                } else {
                    RoutingDecision.REJECTED
                },
            ),
        )
        return if (largeResult.decision == ModelDecision.ACCEPT) {
            acceptedCase(
                evaluationCase = evaluationCase,
                result = largeResult,
                routingDecision = RoutingDecision.ACCEPTED_ON_LARGE,
                attempts = attempts,
            ).also {
                progressReporter.onCaseStage(caseId = evaluationCase.caseId, stage = "pro.accept")
            }
        } else {
            rejectedCase(
                evaluationCase = evaluationCase,
                candidate = largeResult.candidate ?: smallResult.candidate,
                reasons = largeResult.reasons,
                attempts = attempts,
            ).also {
                progressReporter.onCaseStage(
                    caseId = evaluationCase.caseId,
                    stage = "reject",
                    reason = largeResult.reasons.compactReason(),
                )
            }
        }
    }

    @Suppress("LongMethod", "ReturnCount")
    private suspend fun evaluateModel(
        evaluationCase: EvaluationCase,
        endpoint: ModelEndpoint,
    ): ModelEvaluation {
        val gatewayResult = endpoint.gateway.complete(primaryMessages(evaluationCase))
        if (gatewayResult is GatewayResult.Failure) {
            return ModelEvaluation(
                model = endpoint.model,
                role = endpoint.role,
                reasons = listOf(RoutingReason.PRIMARY_REQUEST_FAILED),
                decision = ModelDecision.REJECT,
            )
        }
        gatewayResult as GatewayResult.Success
        val stats = callStats(
            response = gatewayResult.response,
            inputPricePerMillion = endpoint.inputPricePerMillion,
            outputPricePerMillion = endpoint.outputPricePerMillion,
        )
        val candidate = runCatching {
            json.decodeFromString<AssessmentEnvelope>(gatewayResult.response.content)
        }.getOrElse {
            return ModelEvaluation(
                model = endpoint.model,
                role = endpoint.role,
                stats = stats,
                reasons = listOf(RoutingReason.INVALID_ENVELOPE),
                decision = ModelDecision.ESCALATE,
            )
        }
        if (candidate.status == DecisionStatus.FAIL) {
            return ModelEvaluation(
                model = endpoint.model,
                role = endpoint.role,
                stats = stats,
                candidate = candidate,
                reasons = listOf(RoutingReason.STATUS_FAIL),
                decision = ModelDecision.REJECT,
            )
        }
        if (QualityConstraints.validate(candidate, evaluationCase.input).isNotEmpty()) {
            return ModelEvaluation(
                model = endpoint.model,
                role = endpoint.role,
                stats = stats,
                candidate = candidate,
                reasons = listOf(RoutingReason.CONSTRAINTS_FAILED),
                decision = ModelDecision.ESCALATE,
            )
        }
        if (candidate.status == DecisionStatus.UNSURE) {
            return ModelEvaluation(
                model = endpoint.model,
                role = endpoint.role,
                stats = stats,
                candidate = candidate,
                reasons = listOf(RoutingReason.STATUS_UNSURE),
                decision = ModelDecision.ESCALATE,
            )
        }
        if (candidate.confidenceScore < config.confidenceThreshold) {
            return ModelEvaluation(
                model = endpoint.model,
                role = endpoint.role,
                stats = stats,
                candidate = candidate,
                reasons = listOf(RoutingReason.CONFIDENCE_BELOW_THRESHOLD),
                decision = ModelDecision.ESCALATE,
            )
        }
        return ModelEvaluation(
            model = endpoint.model,
            role = endpoint.role,
            stats = stats,
            candidate = candidate,
            decision = ModelDecision.ACCEPT,
        )
    }

    private fun acceptedCase(
        evaluationCase: EvaluationCase,
        result: ModelEvaluation,
        routingDecision: RoutingDecision,
        attempts: List<AttemptReport>,
    ): CaseReport =
        CaseReport(
            caseId = evaluationCase.caseId,
            sourceIndex = evaluationCase.sourceIndex,
            scenario = evaluationCase.scenario,
            input = evaluationCase.input,
            expected = evaluationCase.expected,
            accepted = true,
            finalCandidate = result.candidate,
            reasons = emptyList(),
            attempts = attempts,
            routingDecision = routingDecision,
            riskLevelMatchesExpected = result.candidate?.answer?.riskLevel == evaluationCase.expected.riskLevel,
            additiveCodesMatchExpected = result.candidate?.answer?.matchedAdditives?.map { it.code }?.toSet() ==
                evaluationCase.expected.matchedAdditives.map { it.code }.toSet(),
        )

    private fun rejectedCase(
        evaluationCase: EvaluationCase,
        candidate: AssessmentEnvelope?,
        reasons: List<String>,
        attempts: List<AttemptReport>,
    ): CaseReport =
        CaseReport(
            caseId = evaluationCase.caseId,
            sourceIndex = evaluationCase.sourceIndex,
            scenario = evaluationCase.scenario,
            input = evaluationCase.input,
            expected = evaluationCase.expected,
            accepted = false,
            finalCandidate = candidate,
            reasons = reasons,
            attempts = attempts,
            routingDecision = RoutingDecision.REJECTED,
            riskLevelMatchesExpected = candidate?.answer?.riskLevel == evaluationCase.expected.riskLevel,
            additiveCodesMatchExpected = candidate?.answer?.matchedAdditives?.map { it.code }?.toSet() ==
                evaluationCase.expected.matchedAdditives.map { it.code }.toSet(),
        )

    private fun primaryMessages(evaluationCase: EvaluationCase): List<ChatMessage> = listOf(
        ChatMessage(
            role = "system",
            content = """
                ${evaluationCase.systemPrompt}
                Верни JSON-envelope без Markdown:
                {"answer":{"risk_level":"low|medium|high|unknown","matched_additives":[{"matched_text":"string","canonical_name":"string","code":"string|null","risk_level":"low|medium|high|unknown","reason":"string"}],"warnings":["string"],"safe_summary":"string","confidence":"low|medium|high"},"confidence_score":0.0,"status":"OK|UNSURE|FAIL"}.
                answer обязан иметь ровно перечисленные ключи. matched_additives обязан быть массивом объектов, не строк.
                JSON обязателен.
            """.trimIndent(),
        ),
        ChatMessage(role = "user", content = json.encodeToString(evaluationCase.input)),
    )

    private fun callStats(
        response: RemoteResponse,
        inputPricePerMillion: Double,
        outputPricePerMillion: Double,
    ): CallStats =
        CallStats(
            latencyMillis = response.latencyMillis,
            inputTokens = response.usage.promptTokens,
            outputTokens = response.usage.completionTokens,
            totalTokens = response.usage.totalTokens,
            costUsd = response.usage.promptTokens * inputPricePerMillion / MILLION +
                response.usage.completionTokens * outputPricePerMillion / MILLION,
        )

    private fun ModelEvaluation.toAttempt(index: Int, routingDecision: RoutingDecision): AttemptReport =
        AttemptReport(
            index = index,
            primary = stats,
            model = model,
            modelRole = role,
            routingDecision = routingDecision,
            candidate = candidate,
            reasons = reasons,
        )

    private fun List<String>.compactReason(): String = joinToString(separator = "; ")

    private companion object {

        const val MILLION = 1_000_000.0
    }
}

private data class ModelEndpoint(
    val gateway: DeepSeekGateway,
    val model: String,
    val role: ModelRole,
    val inputPricePerMillion: Double,
    val outputPricePerMillion: Double,
)

private data class ModelEvaluation(
    val model: String,
    val role: ModelRole,
    val stats: CallStats? = null,
    val candidate: AssessmentEnvelope? = null,
    val reasons: List<String> = emptyList(),
    val decision: ModelDecision,
)

private enum class ModelDecision {

    ACCEPT,
    ESCALATE,
    REJECT,
}

private object RoutingReason {

    const val PRIMARY_REQUEST_FAILED = "primary_request_failed"
    const val INVALID_ENVELOPE = "invalid_envelope"
    const val STATUS_FAIL = "status_fail"
    const val CONSTRAINTS_FAILED = "constraints_failed"
    const val STATUS_UNSURE = "status_unsure"
    const val CONFIDENCE_BELOW_THRESHOLD = "confidence_below_threshold"
}
