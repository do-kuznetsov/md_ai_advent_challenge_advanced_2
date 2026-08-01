package com.sibgear.weather.ai.quality

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

internal sealed interface EmbeddingResult {

    data class Success(
        val vectors: List<List<Double>>,
        val latencyMillis: Long,
        val inputTokens: Int,
    ) : EmbeddingResult

    data class Failure(
        val message: String,
    ) : EmbeddingResult
}

internal interface EmbeddingGateway {

    suspend fun embed(inputs: List<String>): EmbeddingResult
}

internal class OllamaEmbeddingClient(
    private val baseUrl: String,
    private val model: String,
    private val client: HttpClient,
) : EmbeddingGateway, AutoCloseable {

    override suspend fun embed(inputs: List<String>): EmbeddingResult {
        require(inputs.isNotEmpty()) { "Embedding input must not be empty." }

        val startedAt = System.nanoTime()
        return try {
            val response = client.post("${baseUrl.removeSuffix("/")}/api/embed") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(OllamaEmbedRequest(model = model, input = inputs, truncate = false))
            }
            val payload = ollamaJson.decodeFromString<OllamaEmbedResponse>(response.bodyAsText())
            require(payload.embeddings.size == inputs.size) {
                "Ollama returned ${payload.embeddings.size} embeddings for ${inputs.size} inputs."
            }
            EmbeddingResult.Success(
                vectors = payload.embeddings,
                latencyMillis = (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND,
                inputTokens = payload.promptEvalCount,
            )
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            EmbeddingResult.Failure(error.message ?: error::class.simpleName.orEmpty())
        }
    }

    override fun close() {
        client.close()
    }

    internal companion object {

        private const val CONNECT_TIMEOUT_MILLIS = 15_000L
        private const val REQUEST_TIMEOUT_MILLIS = 60_000L
        private const val NANOS_PER_MILLISECOND = 1_000_000

        fun create(baseUrl: String, model: String): OllamaEmbeddingClient =
            OllamaEmbeddingClient(
                baseUrl = baseUrl,
                model = model,
                client = HttpClient {
                    expectSuccess = true
                    install(HttpTimeout) {
                        connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
                        requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
                        socketTimeoutMillis = REQUEST_TIMEOUT_MILLIS
                    }
                    install(ContentNegotiation) {
                        json(qualityJson)
                    }
                },
            )
    }
}

@Serializable
private data class OllamaEmbedRequest(
    val model: String,
    val input: List<String>,
    val truncate: Boolean,
)

@Serializable
private data class OllamaEmbedResponse(
    val embeddings: List<List<Double>>,
    @SerialName("prompt_eval_count")
    val promptEvalCount: Int = 0,
)

private val ollamaJson: Json = Json {

    ignoreUnknownKeys = true
}
