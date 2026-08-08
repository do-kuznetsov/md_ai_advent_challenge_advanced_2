package com.sibgear.weather.ai.injection

import java.nio.file.Path
import kotlin.io.path.Path

internal data class CliConfig(
    val stage: RunStage,
    val provider: GatewayProvider,
    val dataset: Path,
    val model: String,
    val profiles: Set<DefenseProfile>,
    val repetitions: Int,
    val caseIds: Set<String>?,
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
            "--provider",
            "--dataset",
            "--model",
            "--profiles",
            "--repetitions",
            "--case-ids",
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
        val provider = parseProvider(options["--provider"] ?: "deepseek")
        val profiles = parseProfiles(options["--profiles"] ?: "all")
        require(stage == RunStage.DEFENDED || "--profiles" !in options) {
            "--profiles is supported only for --stage defended."
        }
        return CliConfig(
            stage = stage,
            provider = provider,
            dataset = Path(options["--dataset"] ?: DEFAULT_DATASET),
            model = options["--model"] ?: provider.defaultModel(),
            profiles = profiles,
            repetitions = (options["--repetitions"] ?: "1").toInt().also {
                require(it > 0) { "--repetitions must be positive." }
            },
            caseIds = options["--case-ids"]?.split(',')?.map(String::trim)?.toSet()?.also {
                require(it.none(String::isBlank)) { "--case-ids must not contain blank ids." }
            },
            output = Path(options["--output"] ?: defaultOutput(stage)),
            keysFile = Path(options["--keys-file"] ?: ".keys.txt"),
            sourceCommit = options["--source-commit"] ?: "unknown",
            inputPricePerMillion = price(options, "--input-price-per-million", provider.defaultInputPrice()),
            outputPricePerMillion = price(options, "--output-price-per-million", provider.defaultOutputPrice()),
        )
    }

    private fun parseProvider(value: String): GatewayProvider =
        when (value) {
            "deepseek" -> GatewayProvider.DEEPSEEK
            "openrouter" -> GatewayProvider.OPENROUTER
            else -> error("Unknown provider: $value")
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
              --provider deepseek|openrouter
              --dataset ai_training/day12/cases.json
              --model <provider-model-id>
              --profiles sanitization,boundary,validation,all
              --repetitions <positive count>
              --case-ids email-attack,web-attack
              --output ai_training/day12/results/<report>.json
              --keys-file .keys.txt
              --source-commit <git-sha>
              --input-price-per-million 0.14
              --output-price-per-million 0.28
            """.trimIndent(),
        )
    }

    private const val DEFAULT_DATASET = "ai_training/day12/cases.json"
    private const val DEEPSEEK_INPUT_PRICE_PER_MILLION = 0.14
    private const val DEEPSEEK_OUTPUT_PRICE_PER_MILLION = 0.28
    private const val OPENROUTER_INPUT_PRICE_PER_MILLION = 0.05
    private const val OPENROUTER_OUTPUT_PRICE_PER_MILLION = 0.15

    private fun GatewayProvider.defaultModel(): String =
        when (this) {
            GatewayProvider.DEEPSEEK -> "deepseek-v4-flash"
            GatewayProvider.OPENROUTER -> "google/gemma-3-12b-it"
        }

    private fun GatewayProvider.defaultInputPrice(): Double =
        when (this) {
            GatewayProvider.DEEPSEEK -> DEEPSEEK_INPUT_PRICE_PER_MILLION
            GatewayProvider.OPENROUTER -> OPENROUTER_INPUT_PRICE_PER_MILLION
        }

    private fun GatewayProvider.defaultOutputPrice(): Double =
        when (this) {
            GatewayProvider.DEEPSEEK -> DEEPSEEK_OUTPUT_PRICE_PER_MILLION
            GatewayProvider.OPENROUTER -> OPENROUTER_OUTPUT_PRICE_PER_MILLION
        }
}
