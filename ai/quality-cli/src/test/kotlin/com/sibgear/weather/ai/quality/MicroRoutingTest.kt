package com.sibgear.weather.ai.quality

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

internal class MicroRoutingTest {

    @Test
    fun `calibrates on training examples without accepting wrong leave one out predictions`() {
        val examples = listOf(
            EmbeddedRiskExample(RiskLevel.LOW, listOf(1.0, 0.0)),
            EmbeddedRiskExample(RiskLevel.LOW, listOf(0.9, 0.1)),
            EmbeddedRiskExample(RiskLevel.MEDIUM, listOf(0.0, 1.0)),
            EmbeddedRiskExample(RiskLevel.MEDIUM, listOf(0.1, 0.9)),
        )

        val calibration = MicroCalibrationSelector.select(examples, accuracyTarget = 1.0)

        assertEquals(4, calibration.leaveOneOutAccepted)
        assertEquals(1.0, calibration.leaveOneOutAccuracy)
    }

    @Test
    fun `accepts confident micro prediction without calling fallback`() = runTest {
        val embeddings = QueueEmbeddingGateway(
            EmbeddingResult.Success(listOf(listOf(1.0, 0.0)), 7, 3),
        )
        val fallback = QueueDeepSeekGateway()
        val result = evaluator(embeddings, fallback, confidentIndex()).evaluate(
            listOf(TestFixtures.microCase()),
        ).single()

        assertEquals(MicroRoutingDecision.ACCEPTED_ON_MICRO, result.decision)
        assertEquals(RiskLevel.MEDIUM, result.micro?.riskLevel)
        assertEquals(0, fallback.calls)
    }

    @Test
    fun `sends unsure prediction to fallback exactly once`() = runTest {
        val embeddings = QueueEmbeddingGateway(
            EmbeddingResult.Success(listOf(listOf(1.0, 0.0)), 7, 3),
        )
        val fallback = QueueDeepSeekGateway(TestFixtures.gatewayResult("{\"risk_level\":\"medium\"}"))
        val result = evaluator(embeddings, fallback, unsureIndex()).evaluate(
            listOf(TestFixtures.microCase()),
        ).single()
        val report = MicroRoutingReportFactory.create(microConfig(), unsureIndex(), listOf(result))

        assertEquals(MicroRoutingDecision.ACCEPTED_ON_LARGE, result.decision)
        assertEquals(RiskLevel.MEDIUM, result.fallbackRiskLevel)
        assertEquals(1, fallback.calls)
        assertEquals(1, report.overall.fallbackCalls)
        assertEquals(1, report.overall.largeModelCalls)
    }

    @Test
    fun `reference constraint escalates conflicting embedding label`() = runTest {
        val embeddings = QueueEmbeddingGateway(
            EmbeddingResult.Success(listOf(listOf(0.0, 1.0)), 7, 3),
        )
        val fallback = QueueDeepSeekGateway(TestFixtures.gatewayResult("{\"risk_level\":\"medium\"}"))
        val result = evaluator(embeddings, fallback, confidentIndex()).evaluate(
            listOf(TestFixtures.microCase()),
        ).single()

        assertEquals(MicroRoutingDecision.ACCEPTED_ON_LARGE, result.decision)
        assertEquals(RiskLevel.LOW, result.micro?.riskLevel)
        assertEquals(DecisionStatus.UNSURE, result.micro?.status)
        assertEquals(1, fallback.calls)
    }

    @Test
    fun `reference resolver returns highest risk from supplied context`() {
        val input = TestFixtures.input.copy(
            referenceAdditives = listOf(
                TestFixtures.input.referenceAdditives.single().copy(riskLevel = RiskLevel.LOW),
                TestFixtures.input.referenceAdditives.single().copy(riskLevel = RiskLevel.HIGH),
            ),
        )

        assertEquals(RiskLevel.HIGH, ReferenceRiskResolver.resolve(input))
    }

    @Test
    fun `rejects malformed fallback response without another call`() = runTest {
        val embeddings = QueueEmbeddingGateway(
            EmbeddingResult.Success(listOf(listOf(1.0, 0.0)), 7, 3),
        )
        val fallback = QueueDeepSeekGateway(TestFixtures.gatewayResult("{}"))
        val result = evaluator(embeddings, fallback, unsureIndex()).evaluate(
            listOf(TestFixtures.microCase()),
        ).single()

        assertEquals(MicroRoutingDecision.REJECTED, result.decision)
        assertTrue("fallback_invalid_json" in result.reasons)
        assertEquals(1, fallback.calls)
    }

    @Test
    fun `loads sixteen Day 6 eval and fourteen supplemental cases`() {
        val loader = MicroRoutingDatasetLoader(qualityJson)

        val cases = loader.loadEvaluation(
            path = java.nio.file.Path.of("..", "..", "ai_training/dataset/eval.jsonl"),
            supplementalPath = java.nio.file.Path.of("..", "..", "ai_training/day10/supplemental.jsonl"),
            limit = null,
        )

        assertEquals(30, cases.size)
        assertEquals(6, cases.count { it.expectedRiskLevel == RiskLevel.HIGH })
        assertTrue(cases.any { it.scenario == MicroScenario.BOUNDARY })
        assertTrue(cases.any { it.scenario == MicroScenario.COMPLEX })
    }

    private fun evaluator(
        embeddings: EmbeddingGateway,
        fallback: DeepSeekGateway,
        index: MicroRoutingIndex,
    ): MicroRoutingEvaluator =
        MicroRoutingEvaluator(
            index = index,
            embeddingGateway = embeddings,
            fallbackGateway = fallback,
            config = microConfig(),
            json = qualityJson,
        )

    private fun confidentIndex(): MicroRoutingIndex =
        index(calibration = MicroCalibration(0.8, 0.1, 2, 1.0))

    private fun unsureIndex(): MicroRoutingIndex =
        index(calibration = MicroCalibration(1.1, 0.1, 0, 0.0))

    private fun index(calibration: MicroCalibration): MicroRoutingIndex =
        MicroRoutingIndex(
            examples = listOf(
                EmbeddedRiskExample(RiskLevel.MEDIUM, listOf(1.0, 0.0)),
                EmbeddedRiskExample(RiskLevel.LOW, listOf(0.0, 1.0)),
            ),
            calibration = calibration,
            buildLatencyMillis = 10,
            buildInputTokens = 20,
        )

    private fun microConfig(): CliConfig =
        requireNotNull(CliParser.parse(arrayOf("--mode", "micro-routing")))

    private class QueueEmbeddingGateway(
        vararg responses: EmbeddingResult,
    ) : EmbeddingGateway {

        private val queue = ArrayDeque(responses.toList())

        override suspend fun embed(inputs: List<String>): EmbeddingResult = queue.removeFirst()
    }

    private class QueueDeepSeekGateway(
        vararg responses: GatewayResult,
    ) : DeepSeekGateway {

        private val queue = ArrayDeque(responses.toList())
        var calls: Int = 0
            private set

        override suspend fun complete(messages: List<ChatMessage>): GatewayResult {
            calls += 1
            return queue.removeFirst()
        }
    }
}
