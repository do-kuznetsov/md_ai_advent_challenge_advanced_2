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
    val keyNames = config.provider.keyNames()
    val apiKey = requireNotNull(ApiKeyLoader.load(config.keysFile, keyNames)) {
        "${keyNames.joinToString(separator = " or ")} in ${config.keysFile} is required."
    }
    val cases = selectCases(config, DatasetLoader(injectionJson).load(config.dataset))
    createClient(config, apiKey).use { client ->
        val results = (1..config.repetitions).flatMap { repetitionIndex ->
            evaluate(config, client, cases, repetitionIndex)
        }
        val report = RunReportFactory.create(config, Instant.now().toString(), results)
        RunReportWriter(injectionJson).write(report, config.output)
        println(
            "stage=${config.stage.name.lowercase()} provider=${config.provider.name.lowercase()} " +
                "calls=${report.summary.cases} " +
                "successful_attacks=${report.summary.successfulAttacks} " +
                "clean_valid=${report.summary.validCleanOutputs} output=${config.output}",
        )
    }
}

internal fun selectCases(config: CliConfig, cases: List<InjectionCase>): List<InjectionCase> {
    val requested = config.caseIds ?: return cases
    val selected = cases.filter { it.id in requested }
    val missing = requested - selected.map(InjectionCase::id).toSet()
    require(missing.isEmpty()) { "Unknown case ids: ${missing.sorted().joinToString()}" }
    return selected
}

private fun createClient(config: CliConfig, apiKey: String): CloseableLlmGateway =
    when (config.provider) {
        GatewayProvider.DEEPSEEK -> DeepSeekClient.create(apiKey = apiKey, model = config.model)
        GatewayProvider.OPENROUTER -> OpenRouterClient.create(apiKey = apiKey, model = config.model)
    }

private suspend fun evaluate(
    config: CliConfig,
    client: LlmGateway,
    cases: List<InjectionCase>,
    repetitionIndex: Int,
): List<CaseResult> =
    when (config.stage) {
        RunStage.BASELINE ->
            InjectionEvaluator(
                gateway = client,
                json = injectionJson,
                inputPricePerMillion = config.inputPricePerMillion,
                outputPricePerMillion = config.outputPricePerMillion,
            ).evaluate(cases, repetitionIndex)

        RunStage.DEFENDED ->
            DefenseEvaluator(
                gateway = client,
                json = injectionJson,
                inputPricePerMillion = config.inputPricePerMillion,
                outputPricePerMillion = config.outputPricePerMillion,
            ).evaluate(cases, config.profiles, repetitionIndex)
    }

private fun GatewayProvider.keyNames(): List<String> =
    when (this) {
        GatewayProvider.DEEPSEEK -> listOf("DEEPSEEK_API_KEY", "deepseek_api_key")
        GatewayProvider.OPENROUTER -> listOf("OPENROUTER_API_KEY", "openrouter_ai_key")
    }
