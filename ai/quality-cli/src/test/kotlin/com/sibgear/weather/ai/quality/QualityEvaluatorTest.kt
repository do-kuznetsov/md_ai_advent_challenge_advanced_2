package com.sibgear.weather.ai.quality

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

internal class QualityEvaluatorTest {

    @Test
    fun `accepts candidate after successful self check`() = runTest {
        val gateway = QueueGateway(
            TestFixtures.gatewayResult(TestFixtures.envelopeJson()),
            TestFixtures.gatewayResult(TestFixtures.selfCheckJson(DecisionStatus.OK)),
        )

        val result = evaluator(gateway).evaluate(listOf(TestFixtures.evaluationCase())).single()

        assertTrue(result.accepted)
        assertEquals(1, result.attempts.size)
        assertEquals(15, result.attempts.single().selfCheck?.totalTokens)
    }

    @Test
    fun `retries unsure primary candidate and accepts next attempt`() = runTest {
        val unsure = TestFixtures.envelope.copy(status = DecisionStatus.UNSURE)
        val gateway = QueueGateway(
            TestFixtures.gatewayResult(TestFixtures.envelopeJson(unsure)),
            TestFixtures.gatewayResult(TestFixtures.envelopeJson()),
            TestFixtures.gatewayResult(TestFixtures.selfCheckJson(DecisionStatus.OK)),
        )

        val result = evaluator(gateway).evaluate(listOf(TestFixtures.evaluationCase())).single()

        assertTrue(result.accepted)
        assertEquals(2, result.attempts.size)
        assertEquals("Model scoring status is UNSURE", result.attempts.first().reasons.single())
    }

    @Test
    fun `rejects fail status without retry`() = runTest {
        val fail = TestFixtures.envelope.copy(status = DecisionStatus.FAIL)
        val gateway = QueueGateway(TestFixtures.gatewayResult(TestFixtures.envelopeJson(fail)))

        val result = evaluator(gateway).evaluate(listOf(TestFixtures.evaluationCase())).single()

        assertFalse(result.accepted)
        assertEquals(1, result.attempts.size)
        assertEquals("Model scoring status is FAIL", result.reasons.single())
    }

    @Test
    fun `rejects after malformed responses exhaust retry limit`() = runTest {
        val gateway = QueueGateway(
            TestFixtures.gatewayResult("{}"),
            TestFixtures.gatewayResult("{}"),
        )

        val result = evaluator(gateway).evaluate(listOf(TestFixtures.evaluationCase())).single()

        assertFalse(result.accepted)
        assertEquals(2, result.attempts.size)
        assertTrue(result.reasons.single().startsWith("Primary response is not a valid envelope"))
    }

    private fun evaluator(gateway: DeepSeekGateway): QualityEvaluator =
        QualityEvaluator(
            config = CliConfig(
                dataset = java.nio.file.Path.of("fixture.jsonl"),
                checks = setOf(CheckType.SELF_CHECK, CheckType.CONSTRAINTS, CheckType.SCORING),
                scenarios = setOf(Scenario.CLEAN),
                model = "test-model",
                confidenceThreshold = 0.75,
                maxAttempts = 2,
                inputPricePerMillion = 0.14,
                outputPricePerMillion = 0.28,
                output = java.nio.file.Path.of("report.json"),
                keysFile = java.nio.file.Path.of("keys.txt"),
            ),
            gateway = gateway,
            json = qualityJson,
        )

    private class QueueGateway(
        vararg responses: GatewayResult,
    ) : DeepSeekGateway {

        private val queue = ArrayDeque(responses.toList())

        override suspend fun complete(messages: List<ChatMessage>): GatewayResult = queue.removeFirst()
    }
}
