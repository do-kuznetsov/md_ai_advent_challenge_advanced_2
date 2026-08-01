package com.sibgear.weather.ai.quality

import java.nio.file.Files
import kotlin.math.abs
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class CliAndDatasetTest {

    @Test
    fun `uses documented CLI defaults`() {
        val config = requireNotNull(CliParser.parse(emptyArray()))

        assertEquals(CliMode.QUALITY, config.mode)
        assertEquals("ai_training/dataset/eval.jsonl", config.dataset.toString())
        assertEquals(setOf(CheckType.SELF_CHECK, CheckType.CONSTRAINTS, CheckType.SCORING), config.checks)
        assertEquals(setOf(Scenario.CLEAN, Scenario.BOUNDARY, Scenario.NOISY), config.scenarios)
        assertEquals("deepseek-v4-flash", config.model)
        assertEquals(0.75, config.confidenceThreshold)
        assertEquals(2, config.maxAttempts)
        assertEquals(null, config.limit)
    }

    @Test
    fun `uses routing defaults without changing quality defaults`() {
        val config = requireNotNull(CliParser.parse(arrayOf("--mode", "routing")))

        assertEquals(CliMode.ROUTING, config.mode)
        assertEquals(setOf(CheckType.CONSTRAINTS, CheckType.SCORING), config.checks)
        assertEquals("deepseek-v4-flash", config.smallModel)
        assertEquals("deepseek-v4-pro", config.largeModel)
        assertEquals(0.14, config.smallInputPricePerMillion)
        assertEquals(0.28, config.smallOutputPricePerMillion)
        assertEquals(0.435, config.largeInputPricePerMillion)
        assertEquals(0.87, config.largeOutputPricePerMillion)
        assertEquals("ai_training/day8/results/routing-report.json", config.output.toString())
    }

    @Test
    fun `uses Day 9 monolithic defaults`() {
        val config = requireNotNull(CliParser.parse(arrayOf("--mode", "monolithic")))

        assertEquals(CliMode.MONOLITHIC, config.mode)
        assertEquals(emptySet(), config.checks)
        assertEquals(1, config.maxAttempts)
        assertEquals(0.0, config.confidenceThreshold)
        assertEquals("ai_training/day9/results/monolithic-report.json", config.output.toString())
    }

    @Test
    fun `uses Day 9 multi stage defaults`() {
        val config = requireNotNull(CliParser.parse(arrayOf("--mode", "multi-stage")))

        assertEquals(CliMode.MULTI_STAGE, config.mode)
        assertEquals(emptySet(), config.checks)
        assertEquals(1, config.maxAttempts)
        assertEquals("ai_training/day9/results/multi-stage-report.json", config.output.toString())
    }

    @Test
    fun `uses Day 10 micro routing defaults`() {
        val config = requireNotNull(CliParser.parse(arrayOf("--mode", "micro-routing")))

        assertEquals(CliMode.MICRO_ROUTING, config.mode)
        assertEquals(emptySet(), config.checks)
        assertEquals("ai_training/dataset/train.jsonl", config.trainDataset.toString())
        assertEquals("ai_training/day10/supplemental.jsonl", config.supplementalDataset.toString())
        assertEquals("nomic-embed-text", config.embeddingModel)
        assertEquals("deepseek-v4-pro", config.largeModel)
        assertEquals(0.95, config.microAccuracyTarget)
        assertEquals("ai_training/day10/results/micro-routing-report.json", config.output.toString())
    }

    @Test
    fun `rejects Day 9 QA and routing options`() {
        assertFailsWith<IllegalArgumentException> {
            CliParser.parse(
                arrayOf(
                    "--mode",
                    "multi-stage",
                    "--max-attempts",
                    "2",
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CliParser.parse(
                arrayOf(
                    "--mode",
                    "monolithic",
                    "--small-model",
                    "deepseek-v4-pro",
                ),
            )
        }
    }

    @Test
    fun `loads compatible Day 6 JSONL row`() {
        val path = Files.createTempFile("quality-cli", ".jsonl")
        val row = DatasetRow(
            messages = listOf(
                ChatMessage("system", "Верни JSON."),
                ChatMessage("user", qualityJson.encodeToString(TestFixtures.input)),
                ChatMessage("assistant", qualityJson.encodeToString(TestFixtures.expected)),
            ),
        )
        path.writeText(compactJson.encodeToString(row) + "\n")

        val cases = DatasetLoader(qualityJson).load(path)

        assertEquals(1, cases.size)
        assertEquals(TestFixtures.input, cases.single().input)
        assertEquals(TestFixtures.expected, cases.single().expected)
        Files.deleteIfExists(path)
    }

    @Test
    fun `loads product without optional name`() {
        val path = Files.createTempFile("quality-cli-null-name", ".jsonl")
        val row = DatasetRow(
            messages = listOf(
                ChatMessage("system", "Верни JSON."),
                ChatMessage("user", compactJson.encodeToString(TestFixtures.input.copy(productName = null))),
                ChatMessage("assistant", compactJson.encodeToString(TestFixtures.expected)),
            ),
        )
        path.writeText(compactJson.encodeToString(row) + "\n")

        val loaded = DatasetLoader(qualityJson).load(path).single()

        assertEquals(null, loaded.input.productName)
        Files.deleteIfExists(path)
    }

    @Test
    fun `calculates additional cost after first primary call`() {
        val primary = CallStats(10, 100, 50, 150, 0.000028)
        val selfCheck = CallStats(5, 50, 25, 75, 0.000014)
        val result = CaseReport(
            caseId = "1-clean",
            sourceIndex = 1,
            scenario = Scenario.CLEAN,
            input = TestFixtures.input,
            expected = TestFixtures.expected,
            accepted = true,
            finalCandidate = TestFixtures.envelope,
            reasons = emptyList(),
            attempts = listOf(
                AttemptReport(index = 1, primary = primary, selfCheck = selfCheck, reasons = emptyList()),
            ),
            riskLevelMatchesExpected = true,
            additiveCodesMatchExpected = true,
        )
        val report = ReportFactory.create(defaultConfig(), listOf(result))

        assertTrue(abs(0.000042 - report.overall.totalCostUsd) < COST_TOLERANCE)
        assertTrue(abs(0.000014 - report.overall.additionalCostUsd) < COST_TOLERANCE)
        assertTrue(report.overall.additionalLatencyMillis == 5L)
    }

    private fun defaultConfig(): CliConfig =
        requireNotNull(CliParser.parse(emptyArray()))

    private companion object {

        val compactJson: Json = Json(qualityJson) { prettyPrint = false }
        const val COST_TOLERANCE = 0.000000001
    }
}
