package com.sibgear.weather.ai.quality

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

internal class QualityConstraintsTest {

    @Test
    fun `accepts assessment matching reference and aggregate risk`() {
        val errors = QualityConstraints.validate(TestFixtures.envelope, TestFixtures.input)

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `rejects hallucinated additive`() {
        val candidate = TestFixtures.envelope.copy(
            answer = TestFixtures.expected.copy(
                matchedAdditives = TestFixtures.expected.matchedAdditives + MatchedAdditive(
                    matchedText = "Е999",
                    canonicalName = "Несуществующая добавка",
                    code = "E999",
                    riskLevel = RiskLevel.HIGH,
                    reason = "Неверно.",
                ),
                riskLevel = RiskLevel.HIGH,
            ),
        )

        val errors = QualityConstraints.validate(candidate, TestFixtures.input)

        assertContains(errors, "matched_additives must exactly match reference_additives")
    }

    @Test
    fun `rejects low confidence score outside range`() {
        val errors = QualityConstraints.validate(TestFixtures.envelope.copy(confidenceScore = 1.2), TestFixtures.input)

        assertContains(errors, "confidence_score must be in 0..1")
    }
}
