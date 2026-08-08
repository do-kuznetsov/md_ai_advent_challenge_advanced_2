package com.sibgear.weather.ai.injection

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest

internal class ProviderAndCliTest {

    @Test
    fun `uses OpenRouter Gemma defaults and parses repetitions`() {
        val config = requireNotNull(
            CliParser.parse(
                arrayOf(
                    "--provider",
                    "openrouter",
                    "--repetitions",
                    "3",
                    "--case-ids",
                    "email-attack,web-attack",
                ),
            ),
        )

        assertEquals(GatewayProvider.OPENROUTER, config.provider)
        assertEquals("google/gemma-3-12b-it", config.model)
        assertEquals(3, config.repetitions)
        assertEquals(setOf("email-attack", "web-attack"), config.caseIds)
        assertEquals(0.05, config.inputPricePerMillion)
        assertEquals(0.15, config.outputPricePerMillion)
    }

    @Test
    fun `selects requested cases and rejects unknown ids`() {
        val config = requireNotNull(
            CliParser.parse(arrayOf("--case-ids", "email-clean,web-attack")),
        )
        val selected = selectCases(config, providerTestCases())

        assertEquals(listOf("email-clean", "web-attack"), selected.map(InjectionCase::id))
    }

    @Test
    fun `loads OpenRouter key by provider names`() {
        val path = Files.createTempFile("day12-openrouter", ".keys")
        path.writeText("openrouter_ai_key=test-value\n")

        val value = ApiKeyLoader.load(path, listOf("OPENROUTER_API_KEY", "openrouter_ai_key"))

        assertEquals("test-value", value)
        Files.deleteIfExists(path)
    }

    @Test
    fun `OpenRouter client uses expected endpoint and parses response`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(OpenRouterClient.CHAT_COMPLETIONS_URL, request.url.toString())
            assertEquals("Bearer test-key", request.headers[HttpHeaders.Authorization])
            respond(
                content =
                    "{\"choices\":[{\"message\":{\"content\":\"{\\\"summary\\\":\\\"ok\\\"}\"}}]," +
                        "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(injectionJson)
            }
        }
        val client = OpenRouterClient("test-key", "google/gemma-3-12b-it", httpClient)

        val result = client.complete(listOf(ChatMessage("user", "test")))

        val success = assertIs<GatewayResult.Success>(result)
        assertEquals("{\"summary\":\"ok\"}", success.content)
        assertEquals(15, success.usage.totalTokens)
        client.close()
    }

    private fun providerTestCases(): List<InjectionCase> =
        listOf(
            InjectionCase(
                id = "email-clean",
                vector = InjectionVector.EMAIL,
                variant = CaseVariant.CLEAN,
                userIntent = "Summarize.",
                content = "Visible email.",
            ),
            InjectionCase(
                id = "web-attack",
                vector = InjectionVector.WEB,
                variant = CaseVariant.ATTACK,
                userIntent = "Answer.",
                content = "Poison.",
            ),
        )
}
