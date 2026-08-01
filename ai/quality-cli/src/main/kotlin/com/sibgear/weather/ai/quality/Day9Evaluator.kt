package com.sibgear.weather.ai.quality

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal interface Day9Evaluator {

    suspend fun evaluate(cases: List<EvaluationCase>): List<Day9CaseReport>
}

internal class MonolithicEvaluator(
    private val config: CliConfig,
    private val gateway: DeepSeekGateway,
    private val json: Json,
) : Day9Evaluator {

    override suspend fun evaluate(cases: List<EvaluationCase>): List<Day9CaseReport> =
        buildList {
            cases.forEach { evaluationCase -> add(evaluateCase(evaluationCase)) }
        }

    @Suppress("ReturnCount")
    private suspend fun evaluateCase(evaluationCase: EvaluationCase): Day9CaseReport {
        val gatewayResult = gateway.complete(monolithicMessages(evaluationCase))
        if (gatewayResult is GatewayResult.Failure) {
            return rejected(
                evaluationCase = evaluationCase,
                stages = listOf(
                    StageReport(
                        stage = InferenceStage.MONOLITHIC,
                        succeeded = false,
                        reasons = listOf("monolithic_request_failed: ${gatewayResult.message}"),
                    ),
                ),
            )
        }
        gatewayResult as GatewayResult.Success
        val stats = callStats(gatewayResult.response, config)
        val answer = runCatching {
            json.decodeFromString<ProductSafetyAssessment>(gatewayResult.response.content)
        }.getOrElse { error ->
            return rejected(
                evaluationCase = evaluationCase,
                stages = listOf(
                    StageReport(
                        stage = InferenceStage.MONOLITHIC,
                        stats = stats,
                        succeeded = false,
                        reasons = listOf("monolithic_invalid_json: ${error.message}"),
                    ),
                ),
            )
        }
        val reasons = QualityConstraints.validate(answer, evaluationCase.input)
        val stage = StageReport(
            stage = InferenceStage.MONOLITHIC,
            stats = stats,
            succeeded = reasons.isEmpty(),
            answer = answer,
            reasons = reasons.map { "monolithic_constraints_failed: $it" },
        )
        return if (stage.succeeded) {
            accepted(evaluationCase, answer, listOf(stage))
        } else {
            rejected(evaluationCase, listOf(stage), answer)
        }
    }

    private fun monolithicMessages(evaluationCase: EvaluationCase): List<ChatMessage> = listOf(
        ChatMessage(
            role = "system",
            content = """
                ${evaluationCase.systemPrompt}
                Верни ровно один JSON-объект без Markdown:
                {"risk_level":"low|medium|high|unknown","matched_additives":[{"matched_text":"string","canonical_name":"string","code":"string|null","risk_level":"low|medium|high|unknown","reason":"string"}],"warnings":["string"],"safe_summary":"string","confidence":"low|medium|high"}.
                Используй только reference_additives из input.
            """.trimIndent(),
        ),
        ChatMessage(role = "user", content = json.encodeToString(evaluationCase.input)),
    )
}

internal class MultiStageEvaluator(
    private val config: CliConfig,
    private val gateway: DeepSeekGateway,
    private val json: Json,
) : Day9Evaluator {

    override suspend fun evaluate(cases: List<EvaluationCase>): List<Day9CaseReport> =
        buildList {
            cases.forEach { evaluationCase -> add(evaluateCase(evaluationCase)) }
        }

    @Suppress("ReturnCount")
    private suspend fun evaluateCase(evaluationCase: EvaluationCase): Day9CaseReport {
        val stages = mutableListOf<StageReport>()
        val normalization = requestNormalization(evaluationCase).also(stages::add)
        if (!normalization.succeeded) {
            return rejected(evaluationCase, stages)
        }
        val normalized = normalization.normalization
            ?: return rejected(evaluationCase, stages)

        val decision = requestDecision(normalized).also(stages::add)
        if (!decision.succeeded) {
            return rejected(evaluationCase, stages)
        }
        val riskDecision = decision.decision
            ?: return rejected(evaluationCase, stages)

        val rendering = requestRendering(evaluationCase, normalized, riskDecision).also(stages::add)
        val answer = rendering.answer
            ?: return rejected(evaluationCase, stages)
        return if (rendering.succeeded) {
            accepted(evaluationCase, answer, stages)
        } else {
            rejected(evaluationCase, stages, answer)
        }
    }

    @Suppress("ReturnCount")
    private suspend fun requestNormalization(evaluationCase: EvaluationCase): StageReport {
        val gatewayResult = gateway.complete(normalizationMessages(evaluationCase))
        if (gatewayResult is GatewayResult.Failure) {
            return StageReport(
                stage = InferenceStage.NORMALIZATION,
                succeeded = false,
                reasons = listOf("normalization_request_failed: ${gatewayResult.message}"),
            )
        }
        gatewayResult as GatewayResult.Success
        val stats = callStats(gatewayResult.response, config)
        val normalization = runCatching {
            json.decodeFromString<NormalizationResult>(gatewayResult.response.content)
        }.getOrElse { error ->
            return StageReport(
                stage = InferenceStage.NORMALIZATION,
                stats = stats,
                succeeded = false,
                reasons = listOf("normalization_invalid_json: ${error.message}"),
            )
        }
        val reasons = Day9Constraints.validate(normalization, evaluationCase.input)
        return StageReport(
            stage = InferenceStage.NORMALIZATION,
            stats = stats,
            succeeded = reasons.isEmpty(),
            normalization = normalization,
            reasons = reasons.map { "normalization_constraints_failed: $it" },
        )
    }

    @Suppress("ReturnCount")
    private suspend fun requestDecision(normalization: NormalizationResult): StageReport {
        val gatewayResult = gateway.complete(decisionMessages(normalization))
        if (gatewayResult is GatewayResult.Failure) {
            return StageReport(
                stage = InferenceStage.DECISION,
                succeeded = false,
                reasons = listOf("decision_request_failed: ${gatewayResult.message}"),
            )
        }
        gatewayResult as GatewayResult.Success
        val stats = callStats(gatewayResult.response, config)
        val decision = runCatching {
            json.decodeFromString<RiskDecision>(gatewayResult.response.content)
        }.getOrElse { error ->
            return StageReport(
                stage = InferenceStage.DECISION,
                stats = stats,
                succeeded = false,
                reasons = listOf("decision_invalid_json: ${error.message}"),
            )
        }
        val reasons = Day9Constraints.validate(decision, normalization)
        return StageReport(
            stage = InferenceStage.DECISION,
            stats = stats,
            succeeded = reasons.isEmpty(),
            decision = decision,
            reasons = reasons.map { "decision_constraints_failed: $it" },
        )
    }

    @Suppress("ReturnCount")
    private suspend fun requestRendering(
        evaluationCase: EvaluationCase,
        normalization: NormalizationResult,
        decision: RiskDecision,
    ): StageReport {
        val gatewayResult = gateway.complete(renderingMessages(evaluationCase, normalization, decision))
        if (gatewayResult is GatewayResult.Failure) {
            return StageReport(
                stage = InferenceStage.RENDERING,
                succeeded = false,
                reasons = listOf("rendering_request_failed: ${gatewayResult.message}"),
            )
        }
        gatewayResult as GatewayResult.Success
        val stats = callStats(gatewayResult.response, config)
        val answer = runCatching {
            json.decodeFromString<ProductSafetyAssessment>(gatewayResult.response.content)
        }.getOrElse { error ->
            return StageReport(
                stage = InferenceStage.RENDERING,
                stats = stats,
                succeeded = false,
                reasons = listOf("rendering_invalid_json: ${error.message}"),
            )
        }
        val reasons = Day9Constraints.validate(answer, decision, evaluationCase.input)
        return StageReport(
            stage = InferenceStage.RENDERING,
            stats = stats,
            succeeded = reasons.isEmpty(),
            answer = answer,
            reasons = reasons.map { "rendering_constraints_failed: $it" },
        )
    }

    private fun normalizationMessages(evaluationCase: EvaluationCase): List<ChatMessage> = listOf(
        ChatMessage(
            role = "system",
            content = """
                Нормализуй product input только по reference_additives. Верни compact JSON без Markdown:
                {"product_name":"string|null","additives":[{"matched_text":"string","canonical_name":"string","code":"string|null","risk_level":"low|medium|high|unknown"}]}.
                additives обязан повторять reference_additives без добавлений, пропусков и изменения порядка.
            """.trimIndent(),
        ),
        ChatMessage(role = "user", content = json.encodeToString(evaluationCase.input)),
    )

    private fun decisionMessages(normalization: NormalizationResult): List<ChatMessage> = listOf(
        ChatMessage(
            role = "system",
            content = """
                Прими решение только по нормализованным additives. Верни compact JSON без Markdown:
                {"selected_additive_keys":["string"],"risk_level":"low|medium|high|unknown","confidence":"low|medium|high"}.
                selected_additive_keys обязан содержать все ключи additives в исходном порядке. risk_level — максимум их risk_level; для пустого списка используй low.
            """.trimIndent(),
        ),
        ChatMessage(
            role = "user",
            content = json.encodeToString(DecisionInput(normalization = normalization)),
        ),
    )

    private fun renderingMessages(
        evaluationCase: EvaluationCase,
        normalization: NormalizationResult,
        decision: RiskDecision,
    ): List<ChatMessage> = listOf(
        ChatMessage(
            role = "system",
            content = """
                Сформируй финальный JSON без Markdown только по normalization и decision:
                {"risk_level":"low|medium|high|unknown","matched_additives":[{"matched_text":"string","canonical_name":"string","code":"string|null","risk_level":"low|medium|high|unknown","reason":"string"}],"warnings":["string"],"safe_summary":"string","confidence":"low|medium|high"}.
                Не добавляй добавки. risk_level и confidence обязаны совпадать с decision.
            """.trimIndent(),
        ),
        ChatMessage(
            role = "user",
            content = json.encodeToString(
                RenderingInput(
                    productName = evaluationCase.input.productName,
                    normalization = normalization,
                    decision = decision,
                ),
            ),
        ),
    )
}

internal object Day9Constraints {

    fun validate(normalization: NormalizationResult, input: ProductInput): List<String> = buildList {
        if (normalization.productName != input.productName) {
            add("product_name must equal input.product_name")
        }
        if (normalization.additives != input.referenceAdditives) {
            add("additives must exactly equal reference_additives")
        }
    }

    fun validate(decision: RiskDecision, normalization: NormalizationResult): List<String> = buildList {
        val expectedKeys = normalization.additives.map(::additiveKey)
        if (decision.selectedAdditiveKeys != expectedKeys) {
            add("selected_additive_keys must equal normalized additive keys")
        }
        if (decision.riskLevel != aggregateRisk(normalization.additives)) {
            add("risk_level must equal the maximum normalized additive risk")
        }
    }

    fun validate(
        answer: ProductSafetyAssessment,
        decision: RiskDecision,
        input: ProductInput,
    ): List<String> = buildList {
        addAll(QualityConstraints.validate(answer, input))
        if (answer.riskLevel != decision.riskLevel) {
            add("final risk_level must equal decision risk_level")
        }
        if (answer.confidence != decision.confidence) {
            add("final confidence must equal decision confidence")
        }
    }

    private fun additiveKey(additive: ReferenceAdditive): String =
        additive.code?.uppercase() ?: additive.matchedText.lowercase()

    private fun aggregateRisk(additives: List<ReferenceAdditive>): RiskLevel =
        additives.maxByOrNull { additive -> riskOrder.getValue(additive.riskLevel) }?.riskLevel ?: RiskLevel.LOW

    private val riskOrder = mapOf(
        RiskLevel.LOW to LOW_RISK_ORDER,
        RiskLevel.UNKNOWN to UNKNOWN_RISK_ORDER,
        RiskLevel.MEDIUM to MEDIUM_RISK_ORDER,
        RiskLevel.HIGH to HIGH_RISK_ORDER,
    )

    private const val LOW_RISK_ORDER = 0
    private const val UNKNOWN_RISK_ORDER = 1
    private const val MEDIUM_RISK_ORDER = 2
    private const val HIGH_RISK_ORDER = 3
}

private fun callStats(response: RemoteResponse, config: CliConfig): CallStats =
    CallStats(
        latencyMillis = response.latencyMillis,
        inputTokens = response.usage.promptTokens,
        outputTokens = response.usage.completionTokens,
        totalTokens = response.usage.totalTokens,
        costUsd = response.usage.promptTokens * config.inputPricePerMillion / MILLION +
            response.usage.completionTokens * config.outputPricePerMillion / MILLION,
    )

private fun accepted(
    evaluationCase: EvaluationCase,
    answer: ProductSafetyAssessment,
    stages: List<StageReport>,
): Day9CaseReport =
    day9CaseReport(
        evaluationCase = evaluationCase,
        accepted = true,
        finalAnswer = answer,
        reasons = emptyList(),
        stages = stages,
    )

private fun rejected(
    evaluationCase: EvaluationCase,
    stages: List<StageReport>,
    finalAnswer: ProductSafetyAssessment? = null,
): Day9CaseReport =
    day9CaseReport(
        evaluationCase = evaluationCase,
        accepted = false,
        finalAnswer = finalAnswer,
        reasons = stages.lastOrNull()?.reasons ?: listOf("no inference attempt"),
        stages = stages,
    )

private fun day9CaseReport(
    evaluationCase: EvaluationCase,
    accepted: Boolean,
    finalAnswer: ProductSafetyAssessment?,
    reasons: List<String>,
    stages: List<StageReport>,
): Day9CaseReport =
    Day9CaseReport(
        caseId = evaluationCase.caseId,
        sourceIndex = evaluationCase.sourceIndex,
        scenario = evaluationCase.scenario,
        input = evaluationCase.input,
        expected = evaluationCase.expected,
        accepted = accepted,
        finalAnswer = finalAnswer,
        reasons = reasons,
        stages = stages,
        riskLevelMatchesExpected = finalAnswer?.riskLevel == evaluationCase.expected.riskLevel,
        additiveCodesMatchExpected = finalAnswer?.matchedAdditives?.map { it.code }?.toSet() ==
            evaluationCase.expected.matchedAdditives.map { it.code }.toSet(),
    )

@Serializable
private data class DecisionInput(
    val normalization: NormalizationResult,
)

@Serializable
private data class RenderingInput(
    val productName: String? = null,
    val normalization: NormalizationResult,
    val decision: RiskDecision,
)

private const val MILLION = 1_000_000.0
