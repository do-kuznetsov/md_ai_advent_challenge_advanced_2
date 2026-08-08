package com.sibgear.weather.ai.injection

import java.nio.file.Path
import kotlin.io.path.Path

internal data class CliConfig(
    val stage: RunStage,
    val dataset: Path,
    val model: String,
    val profiles: Set<DefenseProfile>,
    val output: Path,
    val keysFile: Path,
    val sourceCommit: String,
    val inputPricePerMillion: Double,
    val outputPricePerMillion: Double,
)

internal object CliParser {

    fun parse(args: Array<String>): CliConfig? {
        if (args.contains("--help")) {
            printUsage()
            return null
        }
        require(args.size % 2 == 0) { "Each CLI option must have a value. Use --help." }
        val options = args.toList().chunked(2).associate { it[0] to it[1] }
        require(options.size * 2 == args.size) { "Duplicate CLI option." }
        val supported = setOf(
            "--stage",
            "--dataset",
            "--model",
            "--profiles",
            "--output",
            "--keys-file",
            "--source-commit",
            "--input-price-per-million",
            "--output-price-per-million",
        )
        require(options.keys.all(supported::contains)) {
            "Unknown options: ${options.keys.filterNot(supported::contains).sorted().joinToString()}"
        }
        val stage = parseStage(options["--stage"] ?: "baseline")
        val profiles = parseProfiles(options["--profiles"] ?: "all")
        require(stage == RunStage.DEFENDED || "--profiles" !in options) {
            "--profiles is supported only for --stage defended."
        }
        return CliConfig(
            stage = stage,
            dataset = Path(options["--dataset"] ?: DEFAULT_DATASET),
            model = options["--model"] ?: DEFAULT_MODEL,
            profiles = profiles,
            output = Path(options["--output"] ?: defaultOutput(stage)),
            keysFile = Path(options["--keys-file"] ?: ".keys.txt"),
            sourceCommit = options["--source-commit"] ?: "unknown",
            inputPricePerMillion = price(options, "--input-price-per-million", DEFAULT_INPUT_PRICE),
            outputPricePerMillion = price(options, "--output-price-per-million", DEFAULT_OUTPUT_PRICE),
        )
    }

    private fun parseStage(value: String): RunStage =
        when (value) {
            "baseline" -> RunStage.BASELINE
            "defended" -> RunStage.DEFENDED
            else -> error("Unknown stage: $value")
        }

    private fun parseProfiles(value: String): Set<DefenseProfile> =
        value.split(',').map { item ->
            when (item.trim()) {
                "sanitization" -> DefenseProfile.SANITIZATION
                "boundary" -> DefenseProfile.BOUNDARY
                "validation" -> DefenseProfile.VALIDATION
                "all" -> DefenseProfile.ALL
                else -> error("Unknown defense profile: $item")
            }
        }.toSet().also { require(it.isNotEmpty()) { "--profiles must not be empty." } }

    private fun defaultOutput(stage: RunStage): String =
        when (stage) {
            RunStage.BASELINE -> "ai_training/day12/results/baseline-report.json"
            RunStage.DEFENDED -> "ai_training/day12/results/defended-report.json"
        }

    private fun price(options: Map<String, String>, key: String, defaultValue: Double): Double =
        (options[key] ?: defaultValue.toString()).toDouble().also {
            require(it >= 0.0) { "$key must not be negative." }
        }

    private fun printUsage() {
        println(
            """
            Usage: ./gradlew :ai:prompt-injection-lab:run --args='[options]'
              --stage baseline|defended
              --dataset ai_training/day12/cases.json
              --model deepseek-v4-flash
              --profiles sanitization,boundary,validation,all
              --output ai_training/day12/results/<report>.json
              --keys-file .keys.txt
              --source-commit <git-sha>
              --input-price-per-million 0.14
              --output-price-per-million 0.28
            """.trimIndent(),
        )
    }

    private const val DEFAULT_DATASET = "ai_training/day12/cases.json"
    private const val DEFAULT_MODEL = "deepseek-v4-flash"
    private const val DEFAULT_INPUT_PRICE = 0.14
    private const val DEFAULT_OUTPUT_PRICE = 0.28
}
