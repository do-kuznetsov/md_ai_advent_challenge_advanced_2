package com.sibgear.weather.ai.quality

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ScenarioFactoryTest {

    @Test
    fun `creates deterministic noisy copy without changing reference`() {
        val source = listOf(TestFixtures.evaluationCase())

        val cases = ScenarioFactory.create(source, setOf(Scenario.NOISY))

        assertEquals(1, cases.size)
        assertEquals(Scenario.NOISY, cases.single().scenario)
        assertTrue(cases.single().input.composition.contains("OCR"))
        assertEquals(TestFixtures.input.referenceAdditives, cases.single().input.referenceAdditives)
    }

    @Test
    fun `selects no additive inputs as boundary`() {
        val boundary = TestFixtures.evaluationCase().copy(
            input = TestFixtures.input.copy(referenceAdditives = emptyList()),
            expected = TestFixtures.expected.copy(
                riskLevel = RiskLevel.LOW,
                matchedAdditives = emptyList(),
                warnings = emptyList(),
            ),
        )

        val cases = ScenarioFactory.create(listOf(boundary), setOf(Scenario.BOUNDARY))

        assertEquals(listOf(Scenario.BOUNDARY), cases.map(EvaluationCase::scenario))
    }
}
