package com.sibgear.weather.ai.injection

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

internal class OpenRouterClient(
    private val apiKey: String,
    private val model: String,
    private val client: HttpClient,
) : CloseableLlmGateway {

    override suspend fun complete(messages: List<ChatMessage>): GatewayResult {
        val startedAt = System.nanoTime()
        return try {
            val response = client.post(CHAT_COMPLETIONS_URL) {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                setBody(OpenRouterRequest(model = model, messages = messages))
            }
            val payload = openRouterJson.decodeFromString<OpenRouterResponse>(response.bodyAsText())
            GatewayResult.Success(
                content = payload.choices.firstOrNull()?.message?.content.orEmpty(),
                usage = payload.usage ?: Usage(),
                latencyMillis = (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND,
            )
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            GatewayResult.Failure(error.message ?: error::class.simpleName.orEmpty())
        }
    }

    override fun close() {
        client.close()
    }

    internal companion object {

        const val CHAT_COMPLETIONS_URL = "https://openrouter.ai/api/v1/chat/completions"
        private const val NANOS_PER_MILLISECOND = 1_000_000
        private const val TIMEOUT_MILLIS = 60_000L

        fun create(apiKey: String, model: String): OpenRouterClient =
            OpenRouterClient(
                apiKey = apiKey,
                model = model,
                client = HttpClient {
                    expectSuccess = true
                    install(HttpTimeout) {
                        connectTimeoutMillis = TIMEOUT_MILLIS
                        requestTimeoutMillis = TIMEOUT_MILLIS
                        socketTimeoutMillis = TIMEOUT_MILLIS
                    }
                    install(ContentNegotiation) {
                        json(openRouterJson)
                    }
                },
            )
    }
}

private val openRouterJson: Json = Json { ignoreUnknownKeys = true }

@Serializable
private data class OpenRouterRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.0,
    @SerialName("response_format")
    val responseFormat: OpenRouterResponseFormat = OpenRouterResponseFormat(),
)

@Serializable
private data class OpenRouterResponseFormat(
    val type: String = "json_object",
)

@Serializable
private data class OpenRouterResponse(
    val choices: List<OpenRouterChoice> = emptyList(),
    val usage: Usage? = null,
)

@Serializable
private data class OpenRouterChoice(
    val message: OpenRouterMessage,
)

@Serializable
private data class OpenRouterMessage(
    val content: String? = null,
)
