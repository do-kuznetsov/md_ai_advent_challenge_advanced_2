package com.sibgear.weather.ai.quality

import java.nio.file.Path
import kotlin.io.path.Path

internal data class CliConfig(
    val dataset: Path,
    val checks: Set<CheckType>,
    val scenarios: Set<Scenario>,
    val model: String,
    val confidenceThreshold: Double,
    val maxAttempts: Int,
    val inputPricePerMillion: Double,
    val outputPricePerMillion: Double,
    val output: Path,
    val keysFile: Path,
)

internal object CliParser {

    private const val DEFAULT_DATASET = "ai_training/dataset/eval.jsonl"
    private const val DEFAULT_OUTPUT = "ai_training/day7/results/quality-report.json"

    @Suppress("CyclomaticComplexMethod")
    fun parse(args: Array<String>): CliConfig? {
        if (args.contains("--help")) {
            printUsage()
            return null
        }

        val options = args.toList().windowed(size = 2, step = 2, partialWindows = false).associate { it[0] to it[1] }
        require(args.size % 2 == 0) { "Each CLI option must have a value. Use --help." }
        require(options.size * 2 == args.size) { "Duplicate CLI option." }

        return CliConfig(
            dataset = Path(options["--dataset"] ?: DEFAULT_DATASET),
            checks = parseChecks(options["--checks"] ?: "self-check,constraints,scoring"),
            scenarios = parseScenarios(options["--scenarios"] ?: "clean,boundary,noisy"),
            model = options["--model"] ?: "deepseek-v4-flash",
            confidenceThreshold = (options["--confidence-threshold"] ?: "0.75").toDouble().also {
                require(it in 0.0..1.0) { "--confidence-threshold must be in 0..1." }
            },
            maxAttempts = (options["--max-attempts"] ?: "2").toInt().also {
                require(it > 0) { "--max-attempts must be positive." }
            },
            inputPricePerMillion = (options["--input-price-per-million"] ?: "0.14").toDouble().also {
                require(it >= 0.0) { "--input-price-per-million must not be negative." }
            },
            outputPricePerMillion = (options["--output-price-per-million"] ?: "0.28").toDouble().also {
                require(it >= 0.0) { "--output-price-per-million must not be negative." }
            },
            output = Path(options["--output"] ?: DEFAULT_OUTPUT),
            keysFile = Path(options["--keys-file"] ?: ".keys.txt"),
        )
    }

    private fun parseChecks(value: String): Set<CheckType> =
        value.split(',').map { item ->
            when (item.trim()) {
                "self-check" -> CheckType.SELF_CHECK
                "constraints" -> CheckType.CONSTRAINTS
                "scoring" -> CheckType.SCORING
                else -> error("Unknown check: $item")
            }
        }.toSet().also { require(it.isNotEmpty()) { "--checks must not be empty." } }

    private fun parseScenarios(value: String): Set<Scenario> =
        value.split(',').map { item ->
            when (item.trim()) {
                "clean" -> Scenario.CLEAN
                "boundary" -> Scenario.BOUNDARY
                "noisy" -> Scenario.NOISY
                else -> error("Unknown scenario: $item")
            }
        }.toSet().also { require(it.isNotEmpty()) { "--scenarios must not be empty." } }

    private fun printUsage() {
        println(
            """
            Usage: ./gradlew :ai:quality-cli:run --args='[options]'
              --dataset <path>
              --checks self-check,constraints,scoring
              --scenarios clean,boundary,noisy
              --model deepseek-v4-flash
              --confidence-threshold 0.75
              --max-attempts 2
              --input-price-per-million 0.14
              --output-price-per-million 0.28
              --output <report.json>
              --keys-file .keys.txt
            """.trimIndent(),
        )
    }
}
