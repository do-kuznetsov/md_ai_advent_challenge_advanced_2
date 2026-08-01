package com.sibgear.weather.ai.quality

import kotlinx.serialization.encodeToString

internal object TestFixtures {

    val input = ProductInput(
        composition = "Вода, краситель Е133",
        productName = "Тестовый продукт",
        referenceAdditives = listOf(
            ReferenceAdditive(
                canonicalName = "Синий краситель",
                code = "E133",
                matchedText = "Е133",
                riskLevel = RiskLevel.MEDIUM,
            ),
        ),
    )

    val expected = ProductSafetyAssessment(
        riskLevel = RiskLevel.MEDIUM,
        matchedAdditives = listOf(
            MatchedAdditive(
                matchedText = "Е133",
                canonicalName = "Синий краситель",
                code = "E133",
                riskLevel = RiskLevel.MEDIUM,
                reason = "Есть в переданном справочнике.",
            ),
        ),
        warnings = listOf("Найдена добавка среднего риска."),
        safeSummary = "Есть спорная добавка.",
        confidence = ModelConfidence.HIGH,
    )

    val envelope = AssessmentEnvelope(
        answer = expected,
        confidenceScore = 0.9,
        status = DecisionStatus.OK,
    )

    fun evaluationCase(): EvaluationCase =
        EvaluationCase(
            caseId = "1-clean",
            sourceIndex = 1,
            scenario = Scenario.CLEAN,
            systemPrompt = "Верни JSON.",
            input = input,
            expected = expected,
        )

    fun gatewayResult(content: String, promptTokens: Int = 10, completionTokens: Int = 5): GatewayResult.Success =
        GatewayResult.Success(
            RemoteResponse(
                content = content,
                usage = Usage(
                    promptTokens = promptTokens,
                    completionTokens = completionTokens,
                    totalTokens = promptTokens + completionTokens,
                ),
                latencyMillis = 12,
            ),
        )

    fun envelopeJson(envelope: AssessmentEnvelope = this.envelope): String = qualityJson.encodeToString(envelope)

    fun selfCheckJson(status: DecisionStatus, issues: List<String> = emptyList()): String =
        qualityJson.encodeToString(SelfCheckResult(status = status, issues = issues))
}
