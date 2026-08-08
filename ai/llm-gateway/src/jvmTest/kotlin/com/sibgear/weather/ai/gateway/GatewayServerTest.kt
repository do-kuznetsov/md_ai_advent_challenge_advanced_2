package com.sibgear.weather.ai.gateway

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

internal class GatewayServerTest {

    @Test
    fun healthAndChatUseJsonContract() = testApplication {
        val repository = GatewayRepository.inMemory()
        val service = GatewayService(repository, LlmProvider { ProviderResult("answer", TokenUsage()) })
        application { gatewayModule(service) }
        val jsonClient = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        assertEquals(HttpStatusCode.OK, jsonClient.get("/health").status)
        val response = jsonClient.post("/v1/chat") {
            contentType(ContentType.Application.Json)
            setBody(ChatRequest("clean", inputGuardMode = InputGuardMode.REDACT))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("answer", response.body<ChatTurnResponse>().output?.original)
        repository.close()
    }

    @Test
    fun malformedRequestUsesUnifiedError() = testApplication {
        val repository = GatewayRepository.inMemory()
        val service = GatewayService(repository, LlmProvider { ProviderResult("unused", TokenUsage()) })
        application { gatewayModule(service) }
        val jsonClient = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val response = jsonClient.post("/v1/chat") {
            contentType(ContentType.Application.Json)
            setBody("{")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("INVALID_REQUEST", response.body<ApiError>().code)
        repository.close()
    }
}
