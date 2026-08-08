package com.sibgear.weather.ai.gateway

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.io.path.Path

internal fun runLiveSmoke(): Unit = runBlocking {
    val config = GatewayConfig.fromEnvironment()
    val key = DeepSeekApiKeyLoader.load(config.keysFile)
        ?: error("DEEPSEEK_API_KEY or deepseek_api_key in ${config.keysFile} is required for live smoke")
    val port = System.getenv("LLM_GATEWAY_SMOKE_PORT")?.toIntOrNull() ?: 18091
    val databasePath = Path("ai_training/day13/runtime/live-smoke.sqlite")
    Files.deleteIfExists(databasePath)
    val repository = GatewayRepository.open(databasePath)
    val recordingProvider = RecordingLiveProvider(DeepSeekProvider(key, config.model))
    val service = GatewayService(repository, recordingProvider)
    val server = startGatewayServer(service, "127.0.0.1", port, wait = false)
    val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }
    val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
    }
    val syntheticSecret = "synthetic.day13@example.invalid"
    try {
        val response = client.post("http://127.0.0.1:$port/v1/chat") {
            contentType(ContentType.Application.Json)
            setBody(
                ChatRequest(
                    prompt = "Ответь одним словом: принято. Контакт для примера: $syntheticSecret",
                    inputGuardMode = InputGuardMode.REDACT,
                ),
            )
        }
        check(response.status == HttpStatusCode.OK) { "Live REDACT request failed: ${response.status}" }
        val turn = response.body<ChatTurnResponse>()
        val providerMessages = recordingProvider.lastMessages
        check(providerMessages.none { it.content.contains(syntheticSecret) }) { "Original secret reached DeepSeek" }
        check(providerMessages.any { it.content.contains("[REDACTED_EMAIL]") }) { "Modified placeholder did not reach DeepSeek" }

        val callsBeforeBlock = recordingProvider.callCount
        val blocked = client.post("http://127.0.0.1:$port/v1/chat") {
            contentType(ContentType.Application.Json)
            setBody(ChatRequest("do not send AKIAIOSFODNN7EXAMPLE", inputGuardMode = InputGuardMode.BLOCK))
        }
        check(blocked.status == HttpStatusCode.UnprocessableEntity) { "BLOCK request returned ${blocked.status}" }
        check(recordingProvider.callCount == callsBeforeBlock) { "BLOCK request reached DeepSeek" }

        val report = LiveSmokeReport(
            generatedAtEpochMillis = System.currentTimeMillis(),
            provider = "DeepSeek",
            model = config.model,
            status = "passed",
            gatewayUrl = "http://127.0.0.1:$port",
            database = databasePath.toString(),
            chatId = turn.chatId,
            requestId = turn.requestId,
            assertions = mapOf(
                "real_provider_call" to true,
                "original_secret_absent_from_provider_messages" to true,
                "modified_placeholder_present_in_provider_messages" to true,
                "block_returns_422" to true,
                "block_skips_provider" to true,
            ),
            usage = turn.usage,
            costUsd = turn.costUsd,
            latencyMs = turn.latencyMs,
        )
        val reportPath = Path("ai_training/day13/live-smoke-report.json")
        Files.createDirectories(reportPath.parent)
        Files.writeString(reportPath, json.encodeToString(report) + "\n")
        Files.writeString(
            Path("ai_training/day13/audit-export.json"),
            json.encodeToString(
                repository.chats().chats.mapNotNull { repository.chat(it.chatId) },
            ) + "\n",
        )
        println("live_smoke status=passed request_id=${turn.requestId} chat_id=${turn.chatId} report=$reportPath")
    } finally {
        client.close()
        server.stop(1_000, 2_000)
        repository.close()
    }
}

private class RecordingLiveProvider(
    private val delegate: LlmProvider,
) : LlmProvider {

    var callCount: Int = 0
        private set
    var lastMessages: List<LlmMessage> = emptyList()
        private set

    override suspend fun complete(messages: List<LlmMessage>): ProviderResult {
        callCount += 1
        lastMessages = messages
        return delegate.complete(messages)
    }
}

@Serializable
private data class LiveSmokeReport(
    val generatedAtEpochMillis: Long,
    val provider: String,
    val model: String,
    val status: String,
    val gatewayUrl: String,
    val database: String,
    val chatId: String,
    val requestId: String,
    val assertions: Map<String, Boolean>,
    val usage: TokenUsage,
    val costUsd: String,
    val latencyMs: Long,
)
