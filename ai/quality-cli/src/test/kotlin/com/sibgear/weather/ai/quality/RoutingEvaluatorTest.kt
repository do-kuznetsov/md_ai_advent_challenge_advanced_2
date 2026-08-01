package com.sibgear.weather.ai.quality

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

internal class RoutingEvaluatorTest {

    @Test
    fun `accepts valid Flash candidate without calling Pro`() = runTest {
        val smallGateway = QueueGateway(TestFixtures.gatewayResult(TestFixtures.envelopeJson()))
        val largeGateway = QueueGateway()

        val result = evaluator(smallGateway, largeGateway).evaluate(listOf(TestFixtures.evaluationCase())).single()
        val report = ReportFactory.create(routingConfig(), listOf(result))

        assertTrue(result.accepted)
        assertEquals(RoutingDecision.ACCEPTED_ON_SMALL, result.routingDecision)
        assertEquals(1, smallGateway.calls)
        assertEquals(0, largeGateway.calls)
        assertEquals(1, report.overall.acceptedOnSmall)
        assertEquals(0, report.overall.escalatedToLarge)
        assertEquals(ModelRole.SMALL, result.attempts.single().modelRole)
        assertEquals("deepseek-v4-flash", result.attempts.single().model)
    }

    @Test
    fun `escalates unsure low confidence malformed and constraints failures to Pro`() =
        runTest {
        val lowConfidence = TestFixtures.envelope.copy(confidenceScore = 0.5)
        val invalidConstraints = TestFixtures.envelope.copy(
            answer = TestFixtures.expected.copy(
                matchedAdditives = emptyList(),
                riskLevel = RiskLevel.LOW,
                warnings = emptyList(),
            ),
        )
        val smallGateway = QueueGateway(
            TestFixtures.gatewayResult(
                TestFixtures.envelopeJson(TestFixtures.envelope.copy(status = DecisionStatus.UNSURE)),
            ),
            TestFixtures.gatewayResult(TestFixtures.envelopeJson(lowConfidence)),
            TestFixtures.gatewayResult("{}"),
            TestFixtures.gatewayResult(TestFixtures.envelopeJson(invalidConstraints)),
        )
        val largeGateway = QueueGateway(
            TestFixtures.gatewayResult(TestFixtures.envelopeJson()),
            TestFixtures.gatewayResult(TestFixtures.envelopeJson()),
            TestFixtures.gatewayResult(TestFixtures.envelopeJson()),
            TestFixtures.gatewayResult(TestFixtures.envelopeJson()),
        )
        val cases = (1..4).map { index ->
            TestFixtures.evaluationCase().copy(caseId = "$index-clean")
        }

        val results = evaluator(smallGateway, largeGateway).evaluate(cases)
        val report = ReportFactory.create(routingConfig(), results)

        assertTrue(results.all(CaseReport::accepted))
        assertTrue(results.all { it.routingDecision == RoutingDecision.ACCEPTED_ON_LARGE })
        assertEquals(4, smallGateway.calls)
        assertEquals(4, largeGateway.calls)
        assertEquals(4, report.overall.escalatedToLarge)
        assertEquals(4, report.overall.acceptedOnLarge)
        assertEquals("status_unsure", results[0].attempts.first().reasons.single())
        assertEquals("confidence_below_threshold", results[1].attempts.first().reasons.single())
        assertEquals("invalid_envelope", results[2].attempts.first().reasons.single())
        assertEquals("constraints_failed", results[3].attempts.first().reasons.single())
        }

    @Test
    fun `rejects Flash fail without calling Pro`() = runTest {
        val flashFail = TestFixtures.envelope.copy(status = DecisionStatus.FAIL)
        val smallGateway = QueueGateway(TestFixtures.gatewayResult(TestFixtures.envelopeJson(flashFail)))
        val largeGateway = QueueGateway()

        val result = evaluator(smallGateway, largeGateway).evaluate(listOf(TestFixtures.evaluationCase())).single()

        assertFalse(result.accepted)
        assertEquals(RoutingDecision.REJECTED, result.routingDecision)
        assertEquals(1, smallGateway.calls)
        assertEquals(0, largeGateway.calls)
    }

    @Test
    fun `rejects invalid Pro candidate without third call and reports progress`() =
        runTest {
        val smallGateway = QueueGateway(
            TestFixtures.gatewayResult(
                TestFixtures.envelopeJson(TestFixtures.envelope.copy(status = DecisionStatus.UNSURE)),
            ),
        )
        val largeGateway = QueueGateway(TestFixtures.gatewayResult("{}"))
        val progressReporter = RecordingProgressReporter()

        val result = evaluator(smallGateway, largeGateway, progressReporter)
            .evaluate(listOf(TestFixtures.evaluationCase()))
            .single()

        assertFalse(result.accepted)
        assertEquals(1, smallGateway.calls)
        assertEquals(1, largeGateway.calls)
        assertEquals(
            listOf(
                "start",
                "1-clean:flash.start",
                "1-clean:escalate",
                "1-clean:pro.start",
                "1-clean:reject",
            ),
            progressReporter.events,
        )
        }

    @Test
    fun `uses model specific pricing in routing report`() =
        runTest {
        val smallGateway = QueueGateway(
            TestFixtures.gatewayResult(
                TestFixtures.envelopeJson(TestFixtures.envelope.copy(status = DecisionStatus.UNSURE)),
            ),
        )
        val largeGateway = QueueGateway(TestFixtures.gatewayResult(TestFixtures.envelopeJson()))

        val result = evaluator(smallGateway, largeGateway).evaluate(listOf(TestFixtures.evaluationCase())).single()
        val report = ReportFactory.create(routingConfig(), listOf(result))

        assertTrue(abs(report.overall.totalCostUsd - 0.0000115) < COST_TOLERANCE)
        assertEquals(2, report.overall.primaryCalls)
        assertEquals(1, report.overall.escalatedToLarge)
        }

    private fun evaluator(
        smallGateway: DeepSeekGateway,
        largeGateway: DeepSeekGateway,
        progressReporter: ProgressReporter = RecordingProgressReporter(),
    ): RoutingEvaluator =
        RoutingEvaluator(
            config = routingConfig(),
            smallGateway = smallGateway,
            largeGateway = largeGateway,
            json = qualityJson,
            progressReporter = progressReporter,
        )

    private fun routingConfig(): CliConfig =
        requireNotNull(CliParser.parse(arrayOf("--mode", "routing")))

    private class QueueGateway(
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

    private class RecordingProgressReporter : ProgressReporter {

        val events: MutableList<String> = mutableListOf()

        override fun onRunStarted(config: CliConfig, casesCount: Int) {
            events += "start"
        }

        override fun onCaseStage(caseId: String, stage: String, reason: String?) {
            events += "$caseId:$stage"
        }

        override fun onRunFinished(summary: QualitySummary, output: java.nio.file.Path) = Unit
    }

    private companion object {

        const val COST_TOLERANCE = 0.000000001
    }
}
