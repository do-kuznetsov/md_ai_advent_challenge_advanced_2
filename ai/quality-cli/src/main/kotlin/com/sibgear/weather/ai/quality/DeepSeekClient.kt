package com.sibgear.weather.ai.quality

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal sealed interface GatewayResult {

    data class Success(
        val response: RemoteResponse,
    ) : GatewayResult

    data class Failure(
        val message: String,
    ) : GatewayResult
}

internal data class RemoteResponse(
    val content: String,
    val usage: Usage,
    val latencyMillis: Long,
)

internal interface DeepSeekGateway {

    suspend fun complete(messages: List<ChatMessage>): GatewayResult
}

internal class DeepSeekClient(
    private val apiKey: String,
    private val model: String,
    private val client: HttpClient,
) : DeepSeekGateway, AutoCloseable {

    override suspend fun complete(messages: List<ChatMessage>): GatewayResult {
        val startedAt = System.nanoTime()
        return try {
            val response = client.post(CHAT_COMPLETIONS_URL) {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                setBody(
                    DeepSeekRequest(
                        model = model,
                        messages = messages,
                    ),
                )
            }
            val payload = response.body<DeepSeekResponse>()
            val content = payload.choices.firstOrNull()?.message?.content.orEmpty()
            GatewayResult.Success(
                RemoteResponse(
                    content = content,
                    usage = payload.usage ?: Usage(),
                    latencyMillis = (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND,
                ),
            )
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            GatewayResult.Failure(error.message ?: error::class.simpleName.orEmpty())
        }
    }

    override fun close() {
        client.close()
    }

    internal companion object {

        const val CHAT_COMPLETIONS_URL = "https://api.deepseek.com/chat/completions"
        private const val NANOS_PER_MILLISECOND = 1_000_000

        fun create(apiKey: String, model: String, json: Json): DeepSeekClient =
            DeepSeekClient(
                apiKey = apiKey,
                model = model,
                client = HttpClient {
                    expectSuccess = true
                    install(ContentNegotiation) {
                        json(json)
                    }
                },
            )
    }
}

internal object ApiKeyLoader {

    private val keyNames = listOf("DEEPSEEK_API_KEY", "deepseek_api_key")

    @Suppress("ReturnCount")
    fun load(keysFile: Path): String? {
        keyNames.firstNotNullOfOrNull(System::getenv)?.let { return it }
        if (!keysFile.exists()) {
            return null
        }
        val values = keysFile.readLines()
            .map(String::trim)
            .filter { it.isNotBlank() && !it.startsWith('#') }
            .mapNotNull(::parseKey)
            .toMap()
        return keyNames.firstNotNullOfOrNull(values::get)
    }

    private fun parseKey(line: String): Pair<String, String>? {
        val normalized = line.removePrefix("export ").trim()
        val separator = normalized.indexOf('=')
        if (separator < 1) {
            return null
        }
        return normalized.substring(0, separator).trim() to normalized.substring(separator + 1).trim().trim('"', '\'')
    }
}

@Serializable
private data class DeepSeekRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.0,
    @SerialName("response_format")
    val responseFormat: JsonResponseFormat = JsonResponseFormat(),
    val thinking: ThinkingOptions = ThinkingOptions(),
)

@Serializable
private data class JsonResponseFormat(
    val type: String = "json_object",
)

@Serializable
private data class ThinkingOptions(
    val type: String = "disabled",
)

@Serializable
private data class DeepSeekResponse(
    val choices: List<DeepSeekChoice> = emptyList(),
    val usage: Usage? = null,
)

@Serializable
private data class DeepSeekChoice(
    val message: DeepSeekMessage,
)

@Serializable
private data class DeepSeekMessage(
    val content: String? = null,
)
