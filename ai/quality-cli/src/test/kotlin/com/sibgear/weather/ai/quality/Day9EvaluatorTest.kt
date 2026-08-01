package com.sibgear.weather.ai.quality

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString

internal class Day9EvaluatorTest {

    @Test
    fun `monolithic accepts valid answer in one request`() = runTest {
        val gateway = QueueGateway(TestFixtures.gatewayResult(qualityJson.encodeToString(TestFixtures.expected)))

        val result = monolithicEvaluator(gateway).evaluate(listOf(TestFixtures.evaluationCase())).single()

        assertTrue(result.accepted)
        assertEquals(1, gateway.calls)
        assertEquals(listOf(InferenceStage.MONOLITHIC), result.stages.map(StageReport::stage))
        assertEquals(TestFixtures.expected, result.finalAnswer)
    }

    @Test
    fun `monolithic rejects malformed answer without retry`() = runTest {
        val gateway = QueueGateway(TestFixtures.gatewayResult("{}"))

        val result = monolithicEvaluator(gateway).evaluate(listOf(TestFixtures.evaluationCase())).single()

        assertFalse(result.accepted)
        assertEquals(1, gateway.calls)
        assertTrue(result.reasons.single().startsWith("monolithic_invalid_json:"))
    }

    @Test
    fun `multi stage completes three strict requests`() = runTest {
        val normalization = NormalizationResult(
            productName = TestFixtures.input.productName,
            additives = TestFixtures.input.referenceAdditives,
        )
        val decision = RiskDecision(
            selectedAdditiveKeys = listOf("E133"),
            riskLevel = RiskLevel.MEDIUM,
            confidence = ModelConfidence.HIGH,
        )
        val gateway = QueueGateway(
            TestFixtures.gatewayResult(qualityJson.encodeToString(normalization)),
            TestFixtures.gatewayResult(qualityJson.encodeToString(decision)),
            TestFixtures.gatewayResult(qualityJson.encodeToString(TestFixtures.expected)),
        )

        val result = multiStageEvaluator(gateway).evaluate(listOf(TestFixtures.evaluationCase())).single()
        val report = Day9ReportFactory.create(multiStageConfig(), listOf(result))

        assertTrue(result.accepted)
        assertEquals(3, gateway.calls)
        assertEquals(
            listOf(InferenceStage.NORMALIZATION, InferenceStage.DECISION, InferenceStage.RENDERING),
            result.stages.map(StageReport::stage),
        )
        assertEquals(1, report.byStage.getValue(InferenceStage.NORMALIZATION).calls)
        assertEquals(1, report.byStage.getValue(InferenceStage.DECISION).calls)
        assertEquals(1, report.byStage.getValue(InferenceStage.RENDERING).calls)
        assertEquals(45, report.overall.totalTokens)
        assertTrue(gateway.messages[1].last().content.contains("normalization"))
        assertTrue(gateway.messages[2].last().content.contains("decision"))
    }

    @Test
    fun `multi stage stops after invalid normalization`() = runTest {
        val invalidNormalization = NormalizationResult(
            productName = TestFixtures.input.productName,
            additives = emptyList(),
        )
        val gateway = QueueGateway(TestFixtures.gatewayResult(qualityJson.encodeToString(invalidNormalization)))

        val result = multiStageEvaluator(gateway).evaluate(listOf(TestFixtures.evaluationCase())).single()

        assertFalse(result.accepted)
        assertEquals(1, gateway.calls)
        assertEquals(listOf(InferenceStage.NORMALIZATION), result.stages.map(StageReport::stage))
        assertTrue(result.reasons.single().startsWith("normalization_constraints_failed:"))
    }

    @Test
    fun `multi stage stops after invalid decision`() = runTest {
        val normalization = NormalizationResult(
            productName = TestFixtures.input.productName,
            additives = TestFixtures.input.referenceAdditives,
        )
        val invalidDecision = RiskDecision(
            selectedAdditiveKeys = emptyList(),
            riskLevel = RiskLevel.LOW,
            confidence = ModelConfidence.HIGH,
        )
        val gateway = QueueGateway(
            TestFixtures.gatewayResult(qualityJson.encodeToString(normalization)),
            TestFixtures.gatewayResult(qualityJson.encodeToString(invalidDecision)),
        )

        val result = multiStageEvaluator(gateway).evaluate(listOf(TestFixtures.evaluationCase())).single()

        assertFalse(result.accepted)
        assertEquals(2, gateway.calls)
        assertEquals(
            listOf(InferenceStage.NORMALIZATION, InferenceStage.DECISION),
            result.stages.map(StageReport::stage),
        )
        assertTrue(result.reasons.all { it.startsWith("decision_constraints_failed:") })
    }

    @Test
    fun `multi stage records invalid final answer`() = runTest {
        val normalization = NormalizationResult(
            productName = TestFixtures.input.productName,
            additives = TestFixtures.input.referenceAdditives,
        )
        val decision = RiskDecision(
            selectedAdditiveKeys = listOf("E133"),
            riskLevel = RiskLevel.MEDIUM,
            confidence = ModelConfidence.HIGH,
        )
        val invalidAnswer = TestFixtures.expected.copy(riskLevel = RiskLevel.LOW)
        val gateway = QueueGateway(
            TestFixtures.gatewayResult(qualityJson.encodeToString(normalization)),
            TestFixtures.gatewayResult(qualityJson.encodeToString(decision)),
            TestFixtures.gatewayResult(qualityJson.encodeToString(invalidAnswer)),
        )

        val result = multiStageEvaluator(gateway).evaluate(listOf(TestFixtures.evaluationCase())).single()

        assertFalse(result.accepted)
        assertEquals(3, gateway.calls)
        assertEquals(invalidAnswer, result.finalAnswer)
        assertTrue(result.reasons.all { it.startsWith("rendering_constraints_failed:") })
    }

    private fun monolithicEvaluator(gateway: DeepSeekGateway): MonolithicEvaluator =
        MonolithicEvaluator(config = monolithicConfig(), gateway = gateway, json = qualityJson)

    private fun multiStageEvaluator(gateway: DeepSeekGateway): MultiStageEvaluator =
        MultiStageEvaluator(config = multiStageConfig(), gateway = gateway, json = qualityJson)

    private fun monolithicConfig(): CliConfig =
        requireNotNull(CliParser.parse(arrayOf("--mode", "monolithic")))

    private fun multiStageConfig(): CliConfig =
        requireNotNull(CliParser.parse(arrayOf("--mode", "multi-stage")))

    private class QueueGateway(
        vararg responses: GatewayResult,
    ) : DeepSeekGateway {

        private val queue = ArrayDeque(responses.toList())
        val messages: MutableList<List<ChatMessage>> = mutableListOf()
        var calls: Int = 0
            private set

        override suspend fun complete(messages: List<ChatMessage>): GatewayResult {
            this.messages += messages
            calls += 1
            return queue.removeFirst()
        }
    }
}
