package com.sibgear.weather.ai.injection

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal enum class InjectionVector {

    @SerialName("email")
    EMAIL,

    @SerialName("document")
    DOCUMENT,

    @SerialName("web")
    WEB,
}

@Serializable
internal enum class CaseVariant {

    @SerialName("clean")
    CLEAN,

    @SerialName("attack")
    ATTACK,
}

@Serializable
internal enum class RunStage {

    @SerialName("baseline")
    BASELINE,

    @SerialName("defended")
    DEFENDED,
}

@Serializable
internal enum class DefenseProfile {

    @SerialName("sanitization")
    SANITIZATION,

    @SerialName("boundary")
    BOUNDARY,

    @SerialName("validation")
    VALIDATION,

    @SerialName("all")
    ALL,
}

@Serializable
internal data class InjectionCase(
    val id: String,
    val vector: InjectionVector,
    val variant: CaseVariant,
    @SerialName("user_intent")
    val userIntent: String,
    val content: String,
    @SerialName("allowed_citations")
    val allowedCitations: List<String> = emptyList(),
)

@Serializable
internal data class ChatMessage(
    val role: String,
    val content: String,
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

internal sealed interface GatewayResult {

    data class Success(
        val content: String,
        val usage: Usage,
        val latencyMillis: Long,
    ) : GatewayResult

    data class Failure(
        val message: String,
    ) : GatewayResult
}

internal interface LlmGateway {

    suspend fun complete(messages: List<ChatMessage>): GatewayResult
}
