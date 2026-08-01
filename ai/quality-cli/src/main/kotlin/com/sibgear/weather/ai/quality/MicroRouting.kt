package com.sibgear.weather.ai.quality

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.math.sqrt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class MicroPrediction(
    @SerialName("risk_level")
    val riskLevel: RiskLevel,
    @SerialName("similarity_score")
    val similarityScore: Double,
    val margin: Double,
    val status: DecisionStatus,
)

@Serializable
internal data class MicroCalibration(
    @SerialName("similarity_threshold")
    val similarityThreshold: Double,
    @SerialName("margin_threshold")
    val marginThreshold: Double,
    @SerialName("leave_one_out_accepted")
    val leaveOneOutAccepted: Int,
    @SerialName("leave_one_out_accuracy")
    val leaveOneOutAccuracy: Double,
)

internal data class MicroRoutingIndex(
    val examples: List<EmbeddedRiskExample>,
    val calibration: MicroCalibration,
    val buildLatencyMillis: Long,
    val buildInputTokens: Int,
)

internal data class EmbeddedRiskExample(
    val riskLevel: RiskLevel,
    val vector: List<Double>,
    val referenceRiskLevel: RiskLevel = riskLevel,
)

internal object MicroRoutingIndexBuilder {

    suspend fun build(
        trainingCases: List<MicroRoutingCase>,
        embeddingGateway: EmbeddingGateway,
        accuracyTarget: Double,
    ): Result<MicroRoutingIndex> {
        val inputs = trainingCases.map { case -> MicroRoutingInputFormatter.format(case.input) }
        val response = embeddingGateway.embed(inputs)
        if (response is EmbeddingResult.Failure) {
            return Result.failure(IllegalStateException("Ollama index build failed: ${response.message}"))
        }
        response as EmbeddingResult.Success
        val examples = trainingCases.zip(response.vectors) { case, vector ->
            EmbeddedRiskExample(
                riskLevel = case.expectedRiskLevel,
                referenceRiskLevel = ReferenceRiskResolver.resolve(case.input),
                vector = vector,
            )
        }
        return runCatching {
            require(examples.size >= 2) { "Micro-model requires at least two training examples." }
            MicroRoutingIndex(
                examples = examples,
                calibration = MicroCalibrationSelector.select(examples, accuracyTarget),
                buildLatencyMillis = response.latencyMillis,
                buildInputTokens = response.inputTokens,
            )
        }
    }
}

internal object MicroCalibrationSelector {

    fun select(examples: List<EmbeddedRiskExample>, accuracyTarget: Double): MicroCalibration {
        require(examples.size >= 2) { "Micro-model requires at least two training examples." }

        val candidates = buildList {
            similarityThresholds.forEach { similarityThreshold ->
                val predictions = examples.indices.map { index ->
                    MicroNearestNeighbor.predict(
                        query = examples[index].vector,
                        examples = examples.filterIndexed { candidateIndex, _ -> candidateIndex != index },
                        similarityThreshold = similarityThreshold,
                        marginThreshold = MIN_MARGIN_THRESHOLD,
                    )
                }
                val acceptedIndices = predictions.indices.filter { index ->
                    predictions[index].status == DecisionStatus.OK &&
                        predictions[index].riskLevel == examples[index].referenceRiskLevel
                }
                val accepted = acceptedIndices.size
                val accuracy = acceptedIndices.count { index ->
                    predictions[index].riskLevel == examples[index].riskLevel
                }.toDouble() / accepted.coerceAtLeast(1)
                if (accepted > 0 && accuracy >= accuracyTarget) {
                    add(
                        CalibrationCandidate(
                            similarityThreshold = similarityThreshold,
                            marginThreshold = MIN_MARGIN_THRESHOLD,
                            accepted = accepted,
                            accuracy = accuracy,
                        ),
                    )
                }
            }
        }
        val best = candidates.maxWithOrNull(
            compareBy<CalibrationCandidate> { it.accepted }
                .thenBy { it.accuracy }
                .thenByDescending { it.similarityThreshold }
                .thenByDescending { it.marginThreshold },
        )
        return best?.toCalibration() ?: MicroCalibration(
            similarityThreshold = 1.0,
            marginThreshold = 1.0,
            leaveOneOutAccepted = 0,
            leaveOneOutAccuracy = 0.0,
        )
    }

    private data class CalibrationCandidate(
        val similarityThreshold: Double,
        val marginThreshold: Double,
        val accepted: Int,
        val accuracy: Double,
    ) {

        fun toCalibration(): MicroCalibration =
            MicroCalibration(
                similarityThreshold = similarityThreshold,
                marginThreshold = marginThreshold,
                leaveOneOutAccepted = accepted,
                leaveOneOutAccuracy = accuracy,
            )
    }

    private val similarityThresholds: List<Double> =
        (MIN_SIMILARITY_PERCENT..MAX_SIMILARITY_PERCENT step SIMILARITY_STEP_PERCENT)
            .map { it / PERCENT }

    private const val MIN_SIMILARITY_PERCENT = 50
    private const val MAX_SIMILARITY_PERCENT = 95
    private const val SIMILARITY_STEP_PERCENT = 5
    private const val MIN_MARGIN_THRESHOLD = 0.0
    private const val PERCENT = 100.0
}

internal object MicroNearestNeighbor {

    fun predict(
        query: List<Double>,
        examples: List<EmbeddedRiskExample>,
        similarityThreshold: Double,
        marginThreshold: Double,
    ): MicroPrediction {
        require(examples.isNotEmpty()) { "Micro-model index is empty." }
        val nearest = examples
            .map { example -> example to cosineSimilarity(query, example.vector) }
            .sortedByDescending { (_, similarity) -> similarity }
        val primary = nearest.first()
        val nearestOtherRisk = nearest.firstOrNull { (example, _) -> example.riskLevel != primary.first.riskLevel }
        val margin = primary.second - (nearestOtherRisk?.second ?: 0.0)
        return MicroPrediction(
            riskLevel = primary.first.riskLevel,
            similarityScore = primary.second,
            margin = margin,
            status = if (primary.second >= similarityThreshold && margin >= marginThreshold) {
                DecisionStatus.OK
            } else {
                DecisionStatus.UNSURE
            },
        )
    }

    private fun cosineSimilarity(first: List<Double>, second: List<Double>): Double {
        require(first.size == second.size && first.isNotEmpty()) { "Embedding vectors must have equal non-zero size." }
        val dotProduct = first.zip(second).sumOf { (left, right) -> left * right }
        val firstMagnitude = sqrt(first.sumOf { value -> value * value })
        val secondMagnitude = sqrt(second.sumOf { value -> value * value })
        require(firstMagnitude > 0.0 && secondMagnitude > 0.0) { "Embedding vectors must not be zero vectors." }
        return dotProduct / (firstMagnitude * secondMagnitude)
    }
}

internal object MicroRoutingInputFormatter {

    fun format(input: ProductInput): String =
        buildString {
            input.productName?.let { productName ->
                appendLine("product_name: $productName")
            }
            appendLine("composition: ${input.composition}")
            input.referenceAdditives.forEach { additive ->
                append("reference_additive: ")
                append(additive.code.orEmpty())
                append(' ')
                append(additive.matchedText)
                append(' ')
                appendLine(additive.canonicalName)
            }
        }
}

internal object ReferenceRiskResolver {

    fun resolve(input: ProductInput): RiskLevel =
        input.referenceAdditives.maxByOrNull { additive -> additive.riskLevel.order }?.riskLevel ?: RiskLevel.LOW

    private val RiskLevel.order: Int
        get() = when (this) {
            RiskLevel.LOW -> LOW_ORDER
            RiskLevel.UNKNOWN -> UNKNOWN_ORDER
            RiskLevel.MEDIUM -> MEDIUM_ORDER
            RiskLevel.HIGH -> HIGH_ORDER
        }

    private const val LOW_ORDER = 0
    private const val UNKNOWN_ORDER = 1
    private const val MEDIUM_ORDER = 2
    private const val HIGH_ORDER = 3
}

@Serializable
internal enum class MicroRoutingDecision {

    @SerialName("accepted_on_micro")
    ACCEPTED_ON_MICRO,

    @SerialName("accepted_on_large")
    ACCEPTED_ON_LARGE,

    @SerialName("rejected")
    REJECTED,
}

@Serializable
internal data class MicroRoutingCaseReport(
    val caseId: String,
    val scenario: MicroScenario,
    @SerialName("expected_risk_level")
    val expectedRiskLevel: RiskLevel,
    val micro: MicroPrediction? = null,
    @SerialName("micro_latency_ms")
    val microLatencyMillis: Long? = null,
    @SerialName("fallback_risk_level")
    val fallbackRiskLevel: RiskLevel? = null,
    @SerialName("fallback_stats")
    val fallbackStats: CallStats? = null,
    val decision: MicroRoutingDecision,
    val reasons: List<String>,
)

@Serializable
internal data class MicroRoutingSummary(
    val cases: Int,
    @SerialName("micro_accepted")
    val microAccepted: Int,
    @SerialName("micro_accepted_correct")
    val microAcceptedCorrect: Int,
    @SerialName("micro_accepted_accuracy")
    val microAcceptedAccuracy: Double,
    @SerialName("fallback_calls")
    val fallbackCalls: Int,
    @SerialName("large_model_calls")
    val largeModelCalls: Int,
    val rejected: Int,
    @SerialName("final_correct")
    val finalCorrect: Int,
    @SerialName("final_accuracy")
    val finalAccuracy: Double,
    @SerialName("average_end_to_end_latency_ms")
    val averageEndToEndLatencyMillis: Double,
)

@Serializable
internal data class MicroRoutingReport(
    val embeddingModel: String,
    val largeModel: String,
    val calibration: MicroCalibration,
    @SerialName("index_build_latency_ms")
    val indexBuildLatencyMillis: Long,
    @SerialName("index_build_input_tokens")
    val indexBuildInputTokens: Int,
    val overall: MicroRoutingSummary,
    val results: List<MicroRoutingCaseReport>,
)

internal class MicroRoutingEvaluator(
    private val index: MicroRoutingIndex,
    private val embeddingGateway: EmbeddingGateway,
    private val fallbackGateway: DeepSeekGateway,
    private val config: CliConfig,
    private val json: Json,
) {

    suspend fun evaluate(cases: List<MicroRoutingCase>): List<MicroRoutingCaseReport> = buildList {
        cases.forEach { evaluationCase -> add(evaluateCase(evaluationCase)) }
    }

    private suspend fun evaluateCase(evaluationCase: MicroRoutingCase): MicroRoutingCaseReport {
        val embeddingResult = embeddingGateway.embed(listOf(MicroRoutingInputFormatter.format(evaluationCase.input)))
        val rawPrediction = when (embeddingResult) {
            is EmbeddingResult.Success -> MicroNearestNeighbor.predict(
                query = embeddingResult.vectors.single(),
                examples = index.examples,
                similarityThreshold = index.calibration.similarityThreshold,
                marginThreshold = index.calibration.marginThreshold,
            )

            is EmbeddingResult.Failure -> null
        }
        val microPrediction = rawPrediction?.copy(
            status = if (
                rawPrediction.similarityScore >= index.calibration.similarityThreshold &&
                rawPrediction.riskLevel == ReferenceRiskResolver.resolve(evaluationCase.input)
            ) {
                DecisionStatus.OK
            } else {
                DecisionStatus.UNSURE
            },
        )
        if (microPrediction?.status == DecisionStatus.OK) {
            return MicroRoutingCaseReport(
                caseId = evaluationCase.caseId,
                scenario = evaluationCase.scenario,
                expectedRiskLevel = evaluationCase.expectedRiskLevel,
                micro = microPrediction,
                microLatencyMillis = (embeddingResult as EmbeddingResult.Success).latencyMillis,
                decision = MicroRoutingDecision.ACCEPTED_ON_MICRO,
                reasons = emptyList(),
            )
        }
        return fallback(
            evaluationCase = evaluationCase,
            microPrediction = microPrediction,
            embeddingResult = embeddingResult,
        )
    }

    private suspend fun fallback(
        evaluationCase: MicroRoutingCase,
        microPrediction: MicroPrediction?,
        embeddingResult: EmbeddingResult,
    ): MicroRoutingCaseReport =
        when (val fallbackResult = fallbackGateway.complete(fallbackMessages(evaluationCase.input))) {
            is GatewayResult.Failure -> rejectedCase(
                context = MicroFallbackContext(evaluationCase, microPrediction, embeddingResult),
                fallbackStats = null,
                fallbackReason = "fallback_request_failed",
            )

            is GatewayResult.Success -> parsedFallbackCase(
                context = MicroFallbackContext(evaluationCase, microPrediction, embeddingResult),
                response = fallbackResult.response,
            )
        }

    private fun parsedFallbackCase(
        context: MicroFallbackContext,
        response: RemoteResponse,
    ): MicroRoutingCaseReport {
        val stats = fallbackStats(response)
        return runCatching {
            json.decodeFromString<RiskLevelFallback>(response.content)
        }.fold(
            onSuccess = { candidate -> acceptedFallbackCase(context, stats, candidate) },
            onFailure = {
                rejectedCase(
                    context = context,
                    fallbackStats = stats,
                    fallbackReason = "fallback_invalid_json",
                )
            },
        )
    }

    private fun acceptedFallbackCase(
        context: MicroFallbackContext,
        fallbackStats: CallStats,
        candidate: RiskLevelFallback,
    ): MicroRoutingCaseReport =
        MicroRoutingCaseReport(
            caseId = context.evaluationCase.caseId,
            scenario = context.evaluationCase.scenario,
            expectedRiskLevel = context.evaluationCase.expectedRiskLevel,
            micro = context.microPrediction,
            microLatencyMillis = context.embeddingResult.latencyMillisOrNull(),
            fallbackRiskLevel = candidate.riskLevel,
            fallbackStats = fallbackStats,
            decision = MicroRoutingDecision.ACCEPTED_ON_LARGE,
            reasons = listOf(context.microReason),
        )

    private fun rejectedCase(
        context: MicroFallbackContext,
        fallbackStats: CallStats?,
        fallbackReason: String,
    ): MicroRoutingCaseReport =
        MicroRoutingCaseReport(
            caseId = context.evaluationCase.caseId,
            scenario = context.evaluationCase.scenario,
            expectedRiskLevel = context.evaluationCase.expectedRiskLevel,
            micro = context.microPrediction,
            microLatencyMillis = context.embeddingResult.latencyMillisOrNull(),
            fallbackStats = fallbackStats,
            decision = MicroRoutingDecision.REJECTED,
            reasons = listOf(context.microReason, fallbackReason),
        )

    private data class MicroFallbackContext(
        val evaluationCase: MicroRoutingCase,
        val microPrediction: MicroPrediction?,
        val embeddingResult: EmbeddingResult,
    ) {

        val microReason: String = when (embeddingResult) {
            is EmbeddingResult.Failure -> "micro_embedding_failed"
            is EmbeddingResult.Success -> "micro_unsure"
        }
    }

    private fun EmbeddingResult.latencyMillisOrNull(): Long? =
        (this as? EmbeddingResult.Success)?.latencyMillis

    private fun fallbackMessages(input: ProductInput): List<ChatMessage> = listOf(
        ChatMessage(
            role = "system",
            content = """
                Определи общий risk_level состава только по переданному справочнику добавок.
                Верни JSON без Markdown: {"risk_level":"low|medium|high|unknown"}.
                Не добавляй другие ключи и не выдумывай добавки.
            """.trimIndent(),
        ),
        ChatMessage(role = "user", content = json.encodeToString(input)),
    )

    private fun fallbackStats(response: RemoteResponse): CallStats =
        CallStats(
            latencyMillis = response.latencyMillis,
            inputTokens = response.usage.promptTokens,
            outputTokens = response.usage.completionTokens,
            totalTokens = response.usage.totalTokens,
            costUsd = response.usage.promptTokens * config.largeInputPricePerMillion / MILLION +
                response.usage.completionTokens * config.largeOutputPricePerMillion / MILLION,
        )

    private companion object {

        const val MILLION = 1_000_000.0
    }
}

@Serializable
private data class RiskLevelFallback(
    @SerialName("risk_level")
    val riskLevel: RiskLevel,
)

internal object MicroRoutingReportFactory {

    fun create(config: CliConfig, index: MicroRoutingIndex, results: List<MicroRoutingCaseReport>): MicroRoutingReport =
        MicroRoutingReport(
            embeddingModel = config.embeddingModel,
            largeModel = config.largeModel,
            calibration = index.calibration,
            indexBuildLatencyMillis = index.buildLatencyMillis,
            indexBuildInputTokens = index.buildInputTokens,
            overall = summarize(results),
            results = results,
        )

    private fun summarize(results: List<MicroRoutingCaseReport>): MicroRoutingSummary {
        val microAccepted = results.filter { it.decision == MicroRoutingDecision.ACCEPTED_ON_MICRO }
        val finalCorrect = results.count { report -> finalRiskLevel(report) == report.expectedRiskLevel }
        val totalLatency = results.sumOf { report ->
            (report.microLatencyMillis ?: 0L) + (report.fallbackStats?.latencyMillis ?: 0L)
        }
        return MicroRoutingSummary(
            cases = results.size,
            microAccepted = microAccepted.size,
            microAcceptedCorrect = microAccepted.count { it.micro?.riskLevel == it.expectedRiskLevel },
            microAcceptedAccuracy = microAccepted.count { it.micro?.riskLevel == it.expectedRiskLevel }.toDouble() /
                microAccepted.size.coerceAtLeast(1),
            fallbackCalls = results.count { it.fallbackStats != null || "fallback_request_failed" in it.reasons },
            largeModelCalls = results.count { it.fallbackStats != null || "fallback_request_failed" in it.reasons },
            rejected = results.count { it.decision == MicroRoutingDecision.REJECTED },
            finalCorrect = finalCorrect,
            finalAccuracy = finalCorrect.toDouble() / results.size.coerceAtLeast(1),
            averageEndToEndLatencyMillis = totalLatency.toDouble() / results.size.coerceAtLeast(1),
        )
    }

    private fun finalRiskLevel(report: MicroRoutingCaseReport): RiskLevel? =
        when (report.decision) {
            MicroRoutingDecision.ACCEPTED_ON_MICRO -> report.micro?.riskLevel
            MicroRoutingDecision.ACCEPTED_ON_LARGE -> report.fallbackRiskLevel
            MicroRoutingDecision.REJECTED -> null
        }
}

internal class MicroRoutingReportWriter(
    private val json: Json,
) {

    fun write(report: MicroRoutingReport, output: Path) {
        output.parent?.createDirectories()
        output.writeText(json.encodeToString(report))
    }
}
