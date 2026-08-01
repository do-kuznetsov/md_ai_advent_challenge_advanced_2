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
    val trainDataset: Path = Path("ai_training/dataset/train.jsonl"),
    val supplementalDataset: Path = Path("ai_training/day10/supplemental.jsonl"),
    val embeddingModel: String = "nomic-embed-text",
    val ollamaBaseUrl: String = "http://127.0.0.1:11434",
    val microAccuracyTarget: Double = 0.95,
)

@Suppress("TooManyFunctions")
internal object CliParser {

    private const val DEFAULT_DATASET = "ai_training/dataset/eval.jsonl"
    private const val DAY_7_DEFAULT_OUTPUT = "ai_training/day7/results/quality-report.json"
    private const val DAY_8_DEFAULT_OUTPUT = "ai_training/day8/results/routing-report.json"
    private const val DAY_9_MONOLITHIC_DEFAULT_OUTPUT = "ai_training/day9/results/monolithic-report.json"
    private const val DAY_9_MULTI_STAGE_DEFAULT_OUTPUT = "ai_training/day9/results/multi-stage-report.json"
    private const val DAY_10_DEFAULT_OUTPUT = "ai_training/day10/results/micro-routing-report.json"

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
        validateModeOptions(mode, options)
        val checks = checks(mode, options)

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
            confidenceThreshold = confidenceThreshold(mode, options),
            maxAttempts = maxAttempts(mode, options),
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
            output = Path(options["--output"] ?: defaultOutput(mode)),
            keysFile = Path(options["--keys-file"] ?: ".keys.txt"),
            trainDataset = Path(options["--train-dataset"] ?: "ai_training/dataset/train.jsonl"),
            supplementalDataset = Path(options["--supplemental-dataset"] ?: "ai_training/day10/supplemental.jsonl"),
            embeddingModel = options["--embedding-model"] ?: "nomic-embed-text",
            ollamaBaseUrl = options["--ollama-base-url"] ?: "http://127.0.0.1:11434",
            microAccuracyTarget = (options["--micro-accuracy-target"] ?: "0.95").toDouble().also {
                require(it in 0.0..1.0) { "--micro-accuracy-target must be in 0..1." }
            },
        )

    private fun parseMode(value: String): CliMode =
        when (value) {
            "quality" -> CliMode.QUALITY
            "routing" -> CliMode.ROUTING
            "monolithic" -> CliMode.MONOLITHIC
            "multi-stage" -> CliMode.MULTI_STAGE
            "micro-routing" -> CliMode.MICRO_ROUTING
            else -> error("Unknown mode: $value")
        }

    private fun checks(mode: CliMode, options: Map<String, String>): Set<CheckType> =
        when (mode) {
            CliMode.QUALITY -> parseChecks(options["--checks"] ?: "self-check,constraints,scoring")
            CliMode.ROUTING -> parseChecks(options["--checks"] ?: "constraints,scoring")
            CliMode.MONOLITHIC,
            CliMode.MULTI_STAGE,
            -> emptySet()

            CliMode.MICRO_ROUTING -> emptySet()
        }

    private fun validateModeOptions(mode: CliMode, options: Map<String, String>) {
        when (mode) {
            CliMode.ROUTING -> {
                val checks = checks(mode, options)
                require(checks == ROUTING_CHECKS) {
                    "--mode routing requires --checks constraints,scoring."
                }
                require("--max-attempts" !in options) {
                    "--max-attempts is not supported in --mode routing; routing makes at most two calls."
                }
            }

            CliMode.MONOLITHIC,
            CliMode.MULTI_STAGE,
            -> {
                val unsupported = options.keys.intersect(DAY_9_UNSUPPORTED_OPTIONS)
                require(unsupported.isEmpty()) {
                    "--mode ${mode.name.lowercase().replace('_', '-')} does not support " +
                        unsupported.sorted().joinToString(separator = ", ") + "."
                }
            }

            CliMode.QUALITY,
            CliMode.MICRO_ROUTING,
            -> Unit
        }
    }

    private fun confidenceThreshold(mode: CliMode, options: Map<String, String>): Double =
        if (mode.isDay9()) {
            0.0
        } else {
            (options["--confidence-threshold"] ?: "0.75").toDouble().also {
                require(it in 0.0..1.0) { "--confidence-threshold must be in 0..1." }
            }
        }

    private fun maxAttempts(mode: CliMode, options: Map<String, String>): Int =
        if (mode.isDay9()) {
            1
        } else {
            (options["--max-attempts"] ?: "2").toInt().also {
                require(it > 0) { "--max-attempts must be positive." }
            }
        }

    private fun defaultOutput(mode: CliMode): String =
        when (mode) {
            CliMode.QUALITY -> DAY_7_DEFAULT_OUTPUT
            CliMode.ROUTING -> DAY_8_DEFAULT_OUTPUT
            CliMode.MONOLITHIC -> DAY_9_MONOLITHIC_DEFAULT_OUTPUT
            CliMode.MULTI_STAGE -> DAY_9_MULTI_STAGE_DEFAULT_OUTPUT
            CliMode.MICRO_ROUTING -> DAY_10_DEFAULT_OUTPUT
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
              --mode quality|routing|monolithic|multi-stage|micro-routing
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
              --train-dataset ai_training/dataset/train.jsonl
              --supplemental-dataset ai_training/day10/supplemental.jsonl
              --embedding-model nomic-embed-text
              --ollama-base-url http://127.0.0.1:11434
              --micro-accuracy-target 0.95
            """.trimIndent(),
        )
    }

    private const val DEFAULT_SMALL_INPUT_PRICE = 0.14
    private const val DEFAULT_SMALL_OUTPUT_PRICE = 0.28
    private const val DEFAULT_LARGE_INPUT_PRICE = 0.435
    private const val DEFAULT_LARGE_OUTPUT_PRICE = 0.87

    private val ROUTING_CHECKS: Set<CheckType> = setOf(CheckType.CONSTRAINTS, CheckType.SCORING)

    private val DAY_9_UNSUPPORTED_OPTIONS: Set<String> = setOf(
        "--checks",
        "--max-attempts",
        "--confidence-threshold",
        "--small-model",
        "--large-model",
        "--small-input-price-per-million",
        "--small-output-price-per-million",
        "--large-input-price-per-million",
        "--large-output-price-per-million",
    )

    private fun CliMode.isDay9(): Boolean = this == CliMode.MONOLITHIC || this == CliMode.MULTI_STAGE
}
