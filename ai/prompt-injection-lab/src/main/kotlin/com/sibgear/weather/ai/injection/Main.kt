package com.sibgear.weather.ai.injection

import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

internal val injectionJson: Json = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = false
    prettyPrint = true
}

fun main(args: Array<String>): Unit = runBlocking {
    val config = CliParser.parse(args) ?: return@runBlocking
    val apiKey = requireNotNull(ApiKeyLoader.load(config.keysFile)) {
        "DEEPSEEK_API_KEY or deepseek_api_key in ${config.keysFile} is required."
    }
    val cases = DatasetLoader(injectionJson).load(config.dataset)
    DeepSeekClient.create(apiKey = apiKey, model = config.model).use { client ->
        val results = evaluate(config, client, cases)
        val report = RunReportFactory.create(config, Instant.now().toString(), results)
        RunReportWriter(injectionJson).write(report, config.output)
        println(
            "stage=${config.stage.name.lowercase()} cases=${report.summary.cases} " +
                "successful_attacks=${report.summary.successfulAttacks} " +
                "clean_valid=${report.summary.validCleanOutputs} output=${config.output}",
        )
    }
}

private suspend fun evaluate(
    config: CliConfig,
    client: LlmGateway,
    cases: List<InjectionCase>,
): List<CaseResult> =
    when (config.stage) {
        RunStage.BASELINE ->
            InjectionEvaluator(
                gateway = client,
                json = injectionJson,
                inputPricePerMillion = config.inputPricePerMillion,
                outputPricePerMillion = config.outputPricePerMillion,
            ).evaluate(cases)

        RunStage.DEFENDED ->
            DefenseEvaluator(
                gateway = client,
                json = injectionJson,
                inputPricePerMillion = config.inputPricePerMillion,
                outputPricePerMillion = config.outputPricePerMillion,
            ).evaluate(cases, config.profiles)
    }
