package com.sibgear.weather.ai.gateway

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

internal data class LlmMessage(
    val role: String,
    val content: String,
)

internal data class ProviderResult(
    val content: String,
    val usage: TokenUsage,
)

internal fun interface LlmProvider {

    suspend fun complete(messages: List<LlmMessage>): ProviderResult
}

internal class DeepSeekProvider(
    private val apiKey: String,
    private val model: String = "deepseek-v4-flash",
    private val endpoint: String = "https://api.deepseek.com/chat/completions",
    private val client: HttpClient = defaultClient(),
) : LlmProvider {

    override suspend fun complete(messages: List<LlmMessage>): ProviderResult {
        val response = client.post(endpoint) {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(
                DeepSeekRequest(
                    model = model,
                    messages = messages.map { DeepSeekMessage(it.role, it.content) },
                ),
            )
        }.body<DeepSeekResponse>()
        val usage = response.usage
        return ProviderResult(
            content = response.choices.firstOrNull()?.message?.content
                ?: error("DeepSeek response has no choices"),
            usage = TokenUsage(
                prompt = usage.promptTokens,
                cacheHit = usage.promptCacheHitTokens,
                cacheMiss = usage.promptCacheMissTokens,
                completion = usage.completionTokens,
                total = usage.totalTokens,
            ),
        )
    }

    private companion object {

        fun defaultClient(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}

internal object DeepSeekApiKeyLoader {

    fun load(keysFile: Path): String? {
        sequenceOf("DEEPSEEK_API_KEY", "deepseek_api_key")
            .mapNotNull(System::getenv)
            .firstOrNull(String::isNotBlank)
            ?.let { return it.trim() }
        if (!Files.isRegularFile(keysFile)) return null
        return Files.readAllLines(keysFile)
            .asSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith('#') }
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val name = line.substring(0, separator).trim()
                val value = line.substring(separator + 1).trim().trim('"', '\'')
                value.takeIf { name in setOf("DEEPSEEK_API_KEY", "deepseek_api_key") && it.isNotBlank() }
            }
            .firstOrNull()
    }
}

@Serializable
private data class DeepSeekRequest(
    val model: String,
    val messages: List<DeepSeekMessage>,
    val stream: Boolean = false,
    val thinking: Thinking = Thinking(),
)

@Serializable
private data class Thinking(
    val type: String = "disabled",
)

@Serializable
private data class DeepSeekMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class DeepSeekResponse(
    val choices: List<DeepSeekChoice>,
    val usage: DeepSeekUsage = DeepSeekUsage(),
)

@Serializable
private data class DeepSeekChoice(
    val message: DeepSeekMessage,
)

@Serializable
private data class DeepSeekUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Long = 0,
    @SerialName("prompt_cache_hit_tokens")
    val promptCacheHitTokens: Long = 0,
    @SerialName("prompt_cache_miss_tokens")
    val promptCacheMissTokens: Long = 0,
    @SerialName("completion_tokens")
    val completionTokens: Long = 0,
    @SerialName("total_tokens")
    val totalTokens: Long = 0,
)
