package com.sibgear.weather.ai.gateway

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public enum class InputGuardMode {
    @SerialName("block")
    BLOCK,

    @SerialName("redact")
    REDACT,
}

@Serializable
public enum class GuardDecision {
    ALLOW,
    BLOCK,
    REDACT,
}

@Serializable
public data class GuardFinding(
    public val ruleId: String,
    public val category: String,
    public val start: Int,
    public val end: Int,
    public val replacement: String,
    public val fingerprint: String,
)

@Serializable
public data class GuardedText(
    public val original: String,
    public val modified: String,
    public val decision: GuardDecision,
    public val findings: List<GuardFinding>,
)

@Serializable
public data class TokenUsage(
    public val prompt: Long = 0,
    public val cacheHit: Long = 0,
    public val cacheMiss: Long = 0,
    public val completion: Long = 0,
    public val total: Long = 0,
)

@Serializable
public data class ChatRequest(
    public val prompt: String,
    @SerialName("chat_id")
    public val chatId: String? = null,
    @SerialName("input_guard_mode")
    public val inputGuardMode: InputGuardMode? = null,
)

@Serializable
public data class ChatTurnResponse(
    public val chatId: String,
    public val requestId: String,
    public val turnIndex: Int,
    public val input: GuardedText,
    public val output: GuardedText? = null,
    public val usage: TokenUsage = TokenUsage(),
    public val costUsd: String = "0",
    public val latencyMs: Long = 0,
    public val providerStatus: String,
)

@Serializable
public data class ChatSummary(
    public val chatId: String,
    public val inputGuardMode: InputGuardMode,
    public val promptCount: Int,
    public val createdAtEpochMillis: Long,
    public val updatedAtEpochMillis: Long,
)

@Serializable
public data class ChatDetailResponse(
    public val chat: ChatSummary,
    public val turns: List<ChatTurnResponse>,
)

@Serializable
public data class ChatListResponse(
    public val chats: List<ChatSummary>,
)

@Serializable
public data class DeleteChatResponse(
    public val chatId: String,
    public val deleted: Boolean,
)

@Serializable
public data class HealthResponse(
    public val status: String,
)

@Serializable
public data class ApiError(
    public val requestId: String,
    public val code: String,
    public val message: String,
)
