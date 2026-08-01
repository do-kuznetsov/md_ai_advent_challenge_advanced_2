package com.sibgear.weather.ai.quality

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

internal class OllamaEmbeddingClientTest {

    @Test
    fun `sends batch embed request and parses vectors`() = runTest {
        val engine = MockEngine { request ->
            val body = request.body as TextContent
            assertEquals("/api/embed", request.url.encodedPath)
            assertContains(body.text, "nomic-embed-text")
            respond(
                content = "{\"embeddings\":[[1.0,0.0],[0.0,1.0]],\"prompt_eval_count\":12}",
                headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
            )
        }
        val client = OllamaEmbeddingClient("http://127.0.0.1:11434", "nomic-embed-text", httpClient(engine))

        val result = client.embed(listOf("first", "second"))

        val success = assertIs<EmbeddingResult.Success>(result)
        assertEquals(listOf(listOf(1.0, 0.0), listOf(0.0, 1.0)), success.vectors)
        assertEquals(12, success.inputTokens)
        client.close()
    }

    private fun httpClient(engine: MockEngine): HttpClient =
        HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) {
                json(qualityJson)
            }
        }
}
