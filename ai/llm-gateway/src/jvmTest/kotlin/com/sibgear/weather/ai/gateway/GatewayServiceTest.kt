package com.sibgear.weather.ai.gateway

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class GatewayServiceTest {

    private val repository = GatewayRepository.inMemory()
    private val provider = RecordingProvider()
    private val service = GatewayService(repository, provider)

    @AfterTest
    fun closeRepository() {
        repository.close()
    }

    @Test
    fun blockModeDoesNotCallProviderAndCountsPrompt() = runTest {
        val failure = assertFailsWith<GatewayFailure> {
            service.chat(ChatRequest("aws AKIAIOSFODNN7EXAMPLE", inputGuardMode = InputGuardMode.BLOCK))
        }

        assertEquals(422, failure.httpStatus)
        assertEquals(0, provider.calls.size)
        assertEquals(1, repository.chats().chats.single().promptCount)
        assertEquals("BLOCKED", repository.chats().chats.single().chatId.let { repository.chat(it)!!.turns.single().providerStatus })
    }

    @Test
    fun redactSendsOnlyModifiedPrompt() = runTest {
        val response = service.chat(ChatRequest("email person@example.com", inputGuardMode = InputGuardMode.REDACT))

        val userMessage = provider.calls.single().last()
        assertEquals("email [REDACTED_EMAIL]", userMessage.content)
        assertFalse(provider.calls.flatten().any { it.content.contains("person@example.com") })
        assertEquals("email person@example.com", response.input.original)
    }

    @Test
    fun subsequentContextContainsOnlyModifiedHistory() = runTest {
        val first = service.chat(ChatRequest("first person@example.com", inputGuardMode = InputGuardMode.REDACT))
        service.chat(ChatRequest("second", chatId = first.chatId))

        val secondCall = provider.calls.last()
        assertTrue(secondCall.any { it.role == "user" && it.content == "first [REDACTED_EMAIL]" })
        assertFalse(secondCall.any { it.content.contains("person@example.com") })
    }

    @Test
    fun modeIsImmutable() = runTest {
        val first = service.chat(ChatRequest("clean", inputGuardMode = InputGuardMode.REDACT))

        val failure = assertFailsWith<GatewayFailure> {
            service.chat(ChatRequest("next", chatId = first.chatId, inputGuardMode = InputGuardMode.BLOCK))
        }

        assertEquals("GUARD_MODE_IMMUTABLE", failure.code)
        assertEquals(1, repository.chat(first.chatId)!!.chat.promptCount)
    }

    @Test
    fun fiftiethPromptPassesAndFiftyFirstFails() = runTest {
        var chatId: String? = null
        repeat(50) { index ->
            val response = service.chat(
                ChatRequest(
                    prompt = "prompt-$index",
                    chatId = chatId,
                    inputGuardMode = if (chatId == null) InputGuardMode.REDACT else null,
                ),
            )
            chatId = response.chatId
        }

        val failure = assertFailsWith<GatewayFailure> {
            service.chat(ChatRequest("prompt-51", chatId = chatId))
        }

        assertEquals("CHAT_PROMPT_LIMIT", failure.code)
        assertEquals(50, repository.chat(checkNotNull(chatId))!!.chat.promptCount)
    }

    private class RecordingProvider : LlmProvider {

        val calls = mutableListOf<List<LlmMessage>>()

        override suspend fun complete(messages: List<LlmMessage>): ProviderResult {
            calls += messages
            return ProviderResult("safe answer", TokenUsage(prompt = 10, cacheMiss = 10, completion = 4, total = 14))
        }
    }
}
