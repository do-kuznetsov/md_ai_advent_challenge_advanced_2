package com.sibgear.weather.ai.quality

import java.nio.file.Path
import kotlin.io.path.Path

internal data class CliConfig(
    val mode: CliMode,
    val dataset: Path,
    val checks: Set<CheckType>,
    val scenarios: Set<Scenario>,
    val model: String,
    val smallModel: String,
    val largeModel: String,
    val confidenceThreshold: Double,
    val maxAttempts: Int,
    val limit: Int?,
    val inputPricePerMillion: Double,
    val outputPricePerMillion: Double,
    val smallInputPricePerMillion: Double,
    val smallOutputPricePerMillion: Double,
    val largeInputPricePerMillion: Double,
    val largeOutputPricePerMillion: Double,
    val output: Path,
    val keysFile: Path,
)

internal object CliParser {

    private const val DEFAULT_DATASET = "ai_training/dataset/eval.jsonl"
    private const val DAY_7_DEFAULT_OUTPUT = "ai_training/day7/results/quality-report.json"
    private const val DAY_8_DEFAULT_OUTPUT = "ai_training/day8/results/routing-report.json"

    @Suppress("CyclomaticComplexMethod")
    fun parse(args: Array<String>): CliConfig? {
        if (args.contains("--help")) {
            printUsage()
            return null
        }

        val options = args.toList().windowed(size = 2, step = 2, partialWindows = false).associate { it[0] to it[1] }
        require(args.size % 2 == 0) { "Each CLI option must have a value. Use --help." }
        require(options.size * 2 == args.size) { "Duplicate CLI option." }

        val mode = parseMode(options["--mode"] ?: "quality")
        val defaultChecks = if (mode == CliMode.ROUTING) {
            "constraints,scoring"
        } else {
            "self-check,constraints,scoring"
        }
        val checks = parseChecks(options["--checks"] ?: defaultChecks)
        if (mode == CliMode.ROUTING) {
            require(checks == ROUTING_CHECKS) {
                "--mode routing requires --checks constraints,scoring."
            }
            require("--max-attempts" !in options) {
                "--max-attempts is not supported in --mode routing; routing makes at most two calls."
            }
        }

        return createConfig(
            options = options,
            mode = mode,
            checks = checks,
        )
    }

    @Suppress("CyclomaticComplexMethod")
    private fun createConfig(
        options: Map<String, String>,
        mode: CliMode,
        checks: Set<CheckType>,
    ): CliConfig =
        CliConfig(
            mode = mode,
            dataset = Path(options["--dataset"] ?: DEFAULT_DATASET),
            checks = checks,
            scenarios = parseScenarios(options["--scenarios"] ?: "clean,boundary,noisy"),
            model = options["--model"] ?: "deepseek-v4-flash",
            smallModel = options["--small-model"] ?: "deepseek-v4-flash",
            largeModel = options["--large-model"] ?: "deepseek-v4-pro",
            confidenceThreshold = (options["--confidence-threshold"] ?: "0.75").toDouble().also {
                require(it in 0.0..1.0) { "--confidence-threshold must be in 0..1." }
            },
            maxAttempts = (options["--max-attempts"] ?: "2").toInt().also {
                require(it > 0) { "--max-attempts must be positive." }
            },
            limit = options["--limit"]?.toInt()?.also {
                require(it > 0) { "--limit must be positive." }
            },
            inputPricePerMillion = (options["--input-price-per-million"] ?: "0.14").toDouble().also {
                require(it >= 0.0) { "--input-price-per-million must not be negative." }
            },
            outputPricePerMillion = (options["--output-price-per-million"] ?: "0.28").toDouble().also {
                require(it >= 0.0) { "--output-price-per-million must not be negative." }
            },
            smallInputPricePerMillion = price(
                options,
                "--small-input-price-per-million",
                DEFAULT_SMALL_INPUT_PRICE,
            ),
            smallOutputPricePerMillion = price(
                options,
                "--small-output-price-per-million",
                DEFAULT_SMALL_OUTPUT_PRICE,
            ),
            largeInputPricePerMillion = price(
                options,
                "--large-input-price-per-million",
                DEFAULT_LARGE_INPUT_PRICE,
            ),
            largeOutputPricePerMillion = price(
                options,
                "--large-output-price-per-million",
                DEFAULT_LARGE_OUTPUT_PRICE,
            ),
            output = Path(
                options["--output"] ?: if (mode == CliMode.ROUTING) {
                    DAY_8_DEFAULT_OUTPUT
                } else {
                    DAY_7_DEFAULT_OUTPUT
                },
            ),
            keysFile = Path(options["--keys-file"] ?: ".keys.txt"),
        )

    private fun parseMode(value: String): CliMode =
        when (value) {
            "quality" -> CliMode.QUALITY
            "routing" -> CliMode.ROUTING
            else -> error("Unknown mode: $value")
        }

    private fun price(options: Map<String, String>, name: String, defaultValue: Double): Double =
        (options[name] ?: defaultValue.toString()).toDouble().also {
            require(it >= 0.0) { "$name must not be negative." }
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
              --mode quality|routing
              --dataset <path>
              --checks self-check,constraints,scoring
              --scenarios clean,boundary,noisy
              --model deepseek-v4-flash
              --small-model deepseek-v4-flash
              --large-model deepseek-v4-pro
              --confidence-threshold 0.75
              --max-attempts 2
              --limit <positive count>
              --input-price-per-million 0.14
              --output-price-per-million 0.28
              --small-input-price-per-million 0.14
              --small-output-price-per-million 0.28
              --large-input-price-per-million 0.435
              --large-output-price-per-million 0.87
              --output <report.json>
              --keys-file .keys.txt
            """.trimIndent(),
        )
    }

    private const val DEFAULT_SMALL_INPUT_PRICE = 0.14
    private const val DEFAULT_SMALL_OUTPUT_PRICE = 0.28
    private const val DEFAULT_LARGE_INPUT_PRICE = 0.435
    private const val DEFAULT_LARGE_OUTPUT_PRICE = 0.87

    private val ROUTING_CHECKS: Set<CheckType> = setOf(CheckType.CONSTRAINTS, CheckType.SCORING)
}
