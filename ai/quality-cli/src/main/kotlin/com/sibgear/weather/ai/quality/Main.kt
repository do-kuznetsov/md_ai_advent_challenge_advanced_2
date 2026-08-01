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
    when (config.mode) {
        CliMode.MICRO_ROUTING -> runMicroRoutingMode(config)

        CliMode.QUALITY,
        CliMode.ROUTING,
        CliMode.MONOLITHIC,
        CliMode.MULTI_STAGE,
        -> runExistingMode(config)
    }
}

private suspend fun runExistingMode(config: CliConfig) {
    val apiKey = requireNotNull(ApiKeyLoader.load(config.keysFile)) {
        "DEEPSEEK_API_KEY or deepseek_api_key in ${config.keysFile} is required."
    }
    val source = DatasetLoader(qualityJson).load(config.dataset).let { cases ->
        config.limit?.let(cases::take) ?: cases
    }
    val cases = ScenarioFactory.create(source, config.scenarios)
    require(cases.isNotEmpty()) { "No cases selected by --scenarios." }

    when (config.mode) {
        CliMode.QUALITY -> runQualityMode(config, apiKey, cases)
        CliMode.ROUTING -> runRoutingMode(config, apiKey, cases)
        CliMode.MONOLITHIC,
        CliMode.MULTI_STAGE,
        -> runDay9Mode(config, apiKey, cases)

        CliMode.MICRO_ROUTING -> error("micro-routing must use its dedicated runner.")
    }
}

private suspend fun runQualityMode(
    config: CliConfig,
    apiKey: String,
    cases: List<EvaluationCase>,
) {
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

private suspend fun runRoutingMode(
    config: CliConfig,
    apiKey: String,
    cases: List<EvaluationCase>,
) {
    DeepSeekClient.create(apiKey = apiKey, model = config.smallModel, json = qualityJson).use { smallClient ->
        DeepSeekClient.create(apiKey = apiKey, model = config.largeModel, json = qualityJson).use { largeClient ->
            val progressReporter = ConsoleProgressReporter
            val results = RoutingEvaluator(
                config = config,
                smallGateway = smallClient,
                largeGateway = largeClient,
                json = qualityJson,
                progressReporter = progressReporter,
            ).evaluate(cases)
            val report = ReportFactory.create(config, results)
            ReportWriter(qualityJson).write(report, config.output)
            progressReporter.onRunFinished(summary = report.overall, output = config.output)
        }
    }
}

private suspend fun runMicroRoutingMode(config: CliConfig) {
    val apiKey = requireNotNull(ApiKeyLoader.load(config.keysFile)) {
        "DEEPSEEK_API_KEY or deepseek_api_key in ${config.keysFile} is required."
    }
    val loader = MicroRoutingDatasetLoader(qualityJson)
    val trainingCases = loader.loadTraining(config.trainDataset)
    val evaluationCases = loader.loadEvaluation(
        path = config.dataset,
        supplementalPath = config.supplementalDataset,
        limit = config.limit,
    )
    require(trainingCases.size == DAY_10_TRAIN_CASES) { "Day 10 expects 64 Day 6 training cases." }
    require(evaluationCases.size == DAY_10_EVALUATION_CASES || config.limit != null) {
        "Day 10 expects 30 evaluation cases: 16 Day 6 eval and 14 supplemental cases."
    }

    OllamaEmbeddingClient.create(config.ollamaBaseUrl, config.embeddingModel).use { embeddingClient ->
        val index = MicroRoutingIndexBuilder.build(
            trainingCases = trainingCases,
            embeddingGateway = embeddingClient,
            accuracyTarget = config.microAccuracyTarget,
        ).getOrThrow()
        DeepSeekClient.create(apiKey = apiKey, model = config.largeModel, json = qualityJson).use { fallbackClient ->
            val results = MicroRoutingEvaluator(
                index = index,
                embeddingGateway = embeddingClient,
                fallbackGateway = fallbackClient,
                config = config,
                json = qualityJson,
            ).evaluate(evaluationCases)
            val report = MicroRoutingReportFactory.create(config, index, results)
            MicroRoutingReportWriter(qualityJson).write(report, config.output)
            println(
                "micro_accepted=${report.overall.microAccepted} fallback_calls=${report.overall.fallbackCalls} " +
                    "large_model_calls=${report.overall.largeModelCalls} " +
                    "average_latency_ms=${report.overall.averageEndToEndLatencyMillis} output=${config.output}",
            )
        }
    }
}

private const val DAY_10_TRAIN_CASES = 64
private const val DAY_10_EVALUATION_CASES = 30

private suspend fun runDay9Mode(
    config: CliConfig,
    apiKey: String,
    cases: List<EvaluationCase>,
) {
    DeepSeekClient.create(apiKey = apiKey, model = config.model, json = qualityJson).use { client ->
        val evaluator: Day9Evaluator = when (config.mode) {
            CliMode.MONOLITHIC -> MonolithicEvaluator(config = config, gateway = client, json = qualityJson)
            CliMode.MULTI_STAGE -> MultiStageEvaluator(config = config, gateway = client, json = qualityJson)
            CliMode.QUALITY,
            CliMode.ROUTING,
            CliMode.MICRO_ROUTING,
            -> error("Day 9 evaluator is not supported for ${config.mode.name}.")
        }
        val results = evaluator.evaluate(cases)
        val report = Day9ReportFactory.create(config, results)
        Day9ReportWriter(qualityJson).write(report, config.output)
        println(
            "mode=${config.mode.name.lowercase().replace('_', '-')} accepted=${report.overall.accepted} " +
                "rejected=${report.overall.rejected} latency_ms=${report.overall.totalLatencyMillis} " +
                "cost_usd=${report.overall.totalCostUsd} output=${config.output}",
        )
    }
}
