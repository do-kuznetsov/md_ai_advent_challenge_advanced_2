package com.sibgear.weather.ai.quality

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

internal class DeepSeekClientTest {

    @Test
    fun `sends JSON output request and parses usage`() = runTest {
        val engine = MockEngine { request ->
            val body = request.body as TextContent
            assertContains(body.text, "\"response_format\"")
            assertContains(body.text, "\"thinking\"")
            respond(
                content =
                    """{"choices":[{"message":{"content":"{}"}}],""" +
                        """"usage":{"prompt_tokens":7,"completion_tokens":3,"total_tokens":10}}""",
                headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
            )
        }
        val client = DeepSeekClient("test-key", "test-model", httpClient(engine))

        val result = client.complete(listOf(ChatMessage("user", "JSON")))

        val success = assertIs<GatewayResult.Success>(result)
        assertEquals("{}", success.response.content)
        assertEquals(10, success.response.usage.totalTokens)
        client.close()
    }

    @Test
    fun `returns failure for HTTP error`() = runTest {
        val engine = MockEngine {
            respond(
                content = "{}",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
            )
        }
        val client = DeepSeekClient("test-key", "test-model", httpClient(engine))

        val result = client.complete(listOf(ChatMessage("user", "JSON")))

        assertIs<GatewayResult.Failure>(result)
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
