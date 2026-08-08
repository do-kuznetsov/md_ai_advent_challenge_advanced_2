package com.sibgear.weather.ai.gateway

import java.math.BigDecimal
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class InfrastructureTest {

    @Test
    fun rateLimiterAllowsTenRequestsPerWindow() {
        var now = 1_000L
        val limiter = SlidingWindowRateLimiter(clock = { now })

        repeat(10) { assertTrue(limiter.tryAcquire("127.0.0.1")) }
        assertFalse(limiter.tryAcquire("127.0.0.1"))
        now += 60_001
        assertTrue(limiter.tryAcquire("127.0.0.1"))
    }

    @Test
    fun costUsesCacheHitCacheMissAndOutputRates() {
        val cost = CostCalculator().calculate(
            TokenUsage(cacheHit = 1_000_000, cacheMiss = 1_000_000, completion = 1_000_000),
        )

        assertEquals(BigDecimal("0.4228"), cost)
    }

    @Test
    fun pendingTurnBecomesInterruptedAfterRecovery() {
        val repository = GatewayRepository.inMemory()
        repository.reserveTurn(
            chatId = "chat",
            requestId = "request",
            requestedMode = InputGuardMode.REDACT,
            systemCanary = "canary",
            input = GuardedText("clean", "clean", GuardDecision.ALLOW, emptyList()),
            now = 1,
            maxPrompts = 50,
        )

        repository.recoverInterrupted(2)

        assertEquals("INTERRUPTED", repository.chat("chat")!!.turns.single().providerStatus)
        repository.close()
    }

    @Test
    fun sqliteAuditSurvivesRepositoryReopen() {
        val directory = Files.createTempDirectory("llm-gateway-test")
        val database = directory.resolve("audit.sqlite")
        GatewayRepository.open(database).use { repository ->
            repository.reserveTurn(
                chatId = "chat",
                requestId = "request",
                requestedMode = InputGuardMode.BLOCK,
                systemCanary = "canary",
                input = GuardedText("secret", "secret", GuardDecision.BLOCK, emptyList()),
                now = 1,
                maxPrompts = 50,
            )
        }

        GatewayRepository.open(database).use { reopened ->
            assertEquals("secret", reopened.chat("chat")!!.turns.single().input.original)
        }
    }
}
