package com.sibgear.weather.ai.quality

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

internal val qualityJson: Json = Json {
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
    val source = DatasetLoader(qualityJson).load(config.dataset).let { cases ->
        config.limit?.let(cases::take) ?: cases
    }
    val cases = ScenarioFactory.create(source, config.scenarios)
    require(cases.isNotEmpty()) { "No cases selected by --scenarios." }

    DeepSeekClient.create(apiKey = apiKey, model = config.model, json = qualityJson).use { client ->
        val results = QualityEvaluator(config = config, gateway = client, json = qualityJson).evaluate(cases)
        val report = ReportFactory.create(config, results)
        ReportWriter(qualityJson).write(report, config.output)
        println(
            "accepted=${report.overall.accepted} rejected=${report.overall.rejected} " +
                "retried=${report.overall.retried} output=${config.output}",
        )
    }
}
