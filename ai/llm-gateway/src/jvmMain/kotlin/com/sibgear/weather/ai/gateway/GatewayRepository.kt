package com.sibgear.weather.ai.gateway

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sibgear.weather.ai.gateway.storage.Chat
import com.sibgear.weather.ai.gateway.storage.GatewayDatabase
import com.sibgear.weather.ai.gateway.storage.Turn
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

internal data class StoredChat(
    val id: String,
    val mode: InputGuardMode,
    val systemCanary: String,
    val createdAt: Long,
    val updatedAt: Long,
    val promptCount: Int,
)

internal data class ReservedTurn(
    val chat: StoredChat,
    val requestId: String,
    val turnIndex: Int,
)

internal class GatewayRepository private constructor(
    private val driver: SqlDriver,
    private val database: GatewayDatabase,
    private val json: Json = Json,
) : AutoCloseable {

    private val queries = database.gatewayQueries
    private val lock = Any()

    fun recoverInterrupted(now: Long): Unit = synchronized(lock) {
        queries.interruptPending(now)
    }

    fun reserveTurn(
        chatId: String,
        requestId: String,
        requestedMode: InputGuardMode?,
        systemCanary: String,
        input: GuardedText,
        now: Long,
        maxPrompts: Int,
    ): ReservedTurn = synchronized(lock) {
        var reserved: ReservedTurn? = null
        queries.transaction {
            var chat = queries.selectChatById(chatId).executeAsOneOrNull()?.toStoredChat()
            if (chat == null) {
                val mode = requestedMode
                    ?: throw GatewayFailure(400, "GUARD_MODE_REQUIRED", "input_guard_mode required for new chat")
                queries.insertChat(chatId, mode.name, systemCanary, now, now)
                chat = queries.selectChatById(chatId).executeAsOne().toStoredChat()
            } else if (requestedMode != null && requestedMode != chat.mode) {
                throw GatewayFailure(409, "GUARD_MODE_IMMUTABLE", "Input guard mode cannot be changed")
            }
            if (chat.promptCount >= maxPrompts) {
                throw GatewayFailure(409, "CHAT_PROMPT_LIMIT", "Chat already contains $maxPrompts user prompts")
            }
            val turnIndex = chat.promptCount + 1
            val status = if (input.decision == GuardDecision.BLOCK) "BLOCKED" else "PENDING"
            queries.incrementPromptCount(now, chatId)
            queries.insertTurn(
                request_id = requestId,
                chat_id = chatId,
                turn_index = turnIndex.toLong(),
                input_original = input.original,
                input_modified = input.modified,
                input_decision = input.decision.name,
                input_findings_json = encodeFindings(input.findings),
                provider_status = status,
                created_at_epoch_millis = now,
            )
            reserved = ReservedTurn(
                chat = chat.copy(updatedAt = now, promptCount = turnIndex),
                requestId = requestId,
                turnIndex = turnIndex,
            )
        }
        checkNotNull(reserved)
    }

    fun completeTurn(
        requestId: String,
        output: GuardedText,
        usage: TokenUsage,
        costUsd: String,
        latencyMs: Long,
        now: Long,
    ): Unit = synchronized(lock) {
        queries.completeTurn(
            output_original = output.original,
            output_modified = output.modified,
            output_decision = output.decision.name,
            output_findings_json = encodeFindings(output.findings),
            provider_status = "COMPLETED",
            prompt_tokens = usage.prompt,
            cache_hit_tokens = usage.cacheHit,
            cache_miss_tokens = usage.cacheMiss,
            completion_tokens = usage.completion,
            total_tokens = usage.total,
            cost_usd = costUsd,
            latency_ms = latencyMs,
            completed_at_epoch_millis = now,
            request_id = requestId,
        )
    }

    fun failTurn(requestId: String, latencyMs: Long, now: Long): Unit = synchronized(lock) {
        queries.failTurn("FAILED", latencyMs, now, requestId)
    }

    fun previousContext(chatId: String, turnIndex: Int): List<LlmMessage> = synchronized(lock) {
        queries.selectCompletedTurnsBefore(chatId, turnIndex.toLong()).executeAsList().flatMap { turn ->
            buildList {
                add(LlmMessage("user", turn.input_modified))
                turn.output_modified?.let { add(LlmMessage("assistant", it)) }
            }
        }
    }

    fun chat(chatId: String): ChatDetailResponse? = synchronized(lock) {
        val chat = queries.selectChatById(chatId).executeAsOneOrNull()?.toStoredChat() ?: return null
        ChatDetailResponse(
            chat = chat.toSummary(),
            turns = queries.selectTurnsByChat(chatId).executeAsList().map { it.toResponse() },
        )
    }

    fun systemCanary(chatId: String): String? = synchronized(lock) {
        queries.selectChatById(chatId).executeAsOneOrNull()?.system_canary
    }

    fun chats(): ChatListResponse = synchronized(lock) {
        ChatListResponse(queries.selectChats().executeAsList().map { it.toStoredChat().toSummary() })
    }

    fun delete(chatId: String): Boolean = synchronized(lock) {
        val exists = queries.selectChatById(chatId).executeAsOneOrNull() != null
        if (exists) queries.deleteChat(chatId)
        exists
    }

    override fun close() {
        driver.close()
    }

    private fun Chat.toStoredChat(): StoredChat = StoredChat(
        id = id,
        mode = InputGuardMode.valueOf(input_guard_mode),
        systemCanary = system_canary,
        createdAt = created_at_epoch_millis,
        updatedAt = updated_at_epoch_millis,
        promptCount = prompt_count.toInt(),
    )

    private fun StoredChat.toSummary(): ChatSummary = ChatSummary(
        chatId = id,
        inputGuardMode = mode,
        promptCount = promptCount,
        createdAtEpochMillis = createdAt,
        updatedAtEpochMillis = updatedAt,
    )

    private fun Turn.toResponse(): ChatTurnResponse = ChatTurnResponse(
        chatId = chat_id,
        requestId = request_id,
        turnIndex = turn_index.toInt(),
        input = GuardedText(
            original = input_original,
            modified = input_modified,
            decision = GuardDecision.valueOf(input_decision),
            findings = decodeFindings(input_findings_json),
        ),
        output = output_original?.let { original ->
            GuardedText(
                original = original,
                modified = output_modified.orEmpty(),
                decision = GuardDecision.valueOf(output_decision ?: GuardDecision.ALLOW.name),
                findings = decodeFindings(output_findings_json ?: "[]"),
            )
        },
        usage = TokenUsage(
            prompt = prompt_tokens,
            cacheHit = cache_hit_tokens,
            cacheMiss = cache_miss_tokens,
            completion = completion_tokens,
            total = total_tokens,
        ),
        costUsd = cost_usd,
        latencyMs = latency_ms,
        providerStatus = provider_status,
    )

    private fun encodeFindings(findings: List<GuardFinding>): String =
        json.encodeToString(ListSerializer(GuardFinding.serializer()), findings)

    private fun decodeFindings(value: String): List<GuardFinding> =
        json.decodeFromString(ListSerializer(GuardFinding.serializer()), value)

    internal companion object {

        fun open(path: Path): GatewayRepository {
            val normalized = path.toAbsolutePath().normalize()
            normalized.parent?.let(Files::createDirectories)
            val createSchema = Files.notExists(normalized)
            val driver = JdbcSqliteDriver("jdbc:sqlite:$normalized")
            driver.execute(null, "PRAGMA foreign_keys=ON", 0)
            if (createSchema) GatewayDatabase.Schema.create(driver)
            return GatewayRepository(driver, GatewayDatabase(driver))
        }

        fun inMemory(): GatewayRepository {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            GatewayDatabase.Schema.create(driver)
            driver.execute(null, "PRAGMA foreign_keys=ON", 0)
            return GatewayRepository(driver, GatewayDatabase(driver))
        }
    }
}
