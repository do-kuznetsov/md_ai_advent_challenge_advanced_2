package com.sibgear.weather.ai.quality

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal enum class RiskLevel {

    @SerialName("low")
    LOW,

    @SerialName("medium")
    MEDIUM,

    @SerialName("high")
    HIGH,

    @SerialName("unknown")
    UNKNOWN,
}

@Serializable
internal enum class ModelConfidence {

    @SerialName("low")
    LOW,

    @SerialName("medium")
    MEDIUM,

    @SerialName("high")
    HIGH,
}

@Serializable
internal enum class DecisionStatus {

    @SerialName("OK")
    OK,

    @SerialName("UNSURE")
    UNSURE,

    @SerialName("FAIL")
    FAIL,
}

@Serializable
internal enum class CheckType {

    @SerialName("self-check")
    SELF_CHECK,

    @SerialName("constraints")
    CONSTRAINTS,

    @SerialName("scoring")
    SCORING,
}

@Serializable
internal enum class Scenario {

    @SerialName("clean")
    CLEAN,

    @SerialName("boundary")
    BOUNDARY,

    @SerialName("noisy")
    NOISY,
}

@Serializable
internal enum class CliMode {

    @SerialName("quality")
    QUALITY,

    @SerialName("routing")
    ROUTING,
}

@Serializable
internal enum class ModelRole {

    @SerialName("small")
    SMALL,

    @SerialName("large")
    LARGE,
}

@Serializable
internal enum class RoutingDecision {

    @SerialName("accepted_on_small")
    ACCEPTED_ON_SMALL,

    @SerialName("escalated_to_large")
    ESCALATED_TO_LARGE,

    @SerialName("accepted_on_large")
    ACCEPTED_ON_LARGE,

    @SerialName("rejected")
    REJECTED,
}

@Serializable
internal data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
internal data class DatasetRow(
    val messages: List<ChatMessage>,
)

@Serializable
internal data class ProductInput(
    @SerialName("composition")
    val composition: String,
    @SerialName("product_name")
    val productName: String? = null,
    @SerialName("reference_additives")
    val referenceAdditives: List<ReferenceAdditive>,
)

@Serializable
internal data class ReferenceAdditive(
    @SerialName("canonical_name")
    val canonicalName: String,
    val code: String? = null,
    @SerialName("matched_text")
    val matchedText: String,
    @SerialName("risk_level")
    val riskLevel: RiskLevel,
)

@Serializable
internal data class ProductSafetyAssessment(
    @SerialName("risk_level")
    val riskLevel: RiskLevel,
    @SerialName("matched_additives")
    val matchedAdditives: List<MatchedAdditive>,
    val warnings: List<String>,
    @SerialName("safe_summary")
    val safeSummary: String,
    val confidence: ModelConfidence,
)

@Serializable
internal data class MatchedAdditive(
    @SerialName("matched_text")
    val matchedText: String,
    @SerialName("canonical_name")
    val canonicalName: String,
    val code: String? = null,
    @SerialName("risk_level")
    val riskLevel: RiskLevel,
    val reason: String,
)

@Serializable
internal data class AssessmentEnvelope(
    val answer: ProductSafetyAssessment,
    @SerialName("confidence_score")
    val confidenceScore: Double,
    val status: DecisionStatus,
)

@Serializable
internal data class SelfCheckResult(
    val status: DecisionStatus,
    val issues: List<String>,
)

@Serializable
internal data class Usage(
    @SerialName("prompt_tokens")
    val promptTokens: Int = 0,
    @SerialName("completion_tokens")
    val completionTokens: Int = 0,
    @SerialName("total_tokens")
    val totalTokens: Int = 0,
)
