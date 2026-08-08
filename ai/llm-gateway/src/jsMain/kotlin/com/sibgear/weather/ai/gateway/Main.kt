package com.sibgear.weather.ai.gateway

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.dom.Article
import org.jetbrains.compose.web.dom.Aside
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Code
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.Header
import org.jetbrains.compose.web.dom.Main
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Pre
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Small
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea
import org.jetbrains.compose.web.renderComposable
import org.w3c.fetch.Headers
import org.w3c.fetch.RequestInit

public fun main() {
    document.title = if (window.location.pathname == "/debug") "LLM Gateway Debug" else "LLM Gateway"
    renderComposable(rootElementId = "root") {
        if (window.location.pathname == "/debug") DebugApp() else MainApp()
    }
}

internal fun debugWindowUrl(chatId: String): String = "/debug?chat_id=$chatId"

internal fun debugWindowName(chatId: String): String = "llm-gateway-debug-$chatId"

@Composable
private fun MainApp() {
    val api = remember { BrowserApi() }
    val scope = rememberCoroutineScope()
    var chats by remember { mutableStateOf(emptyList<ChatSummary>()) }
    var selectedChatId by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<ChatDetailResponse?>(null) }
    var prompt by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(InputGuardMode.REDACT) }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }

    suspend fun refresh(select: String? = selectedChatId) {
        chats = api.getChats().chats
        selectedChatId = select
        detail = select?.let { api.getChat(it) }
        detail?.let { selectedMode = it.chat.inputGuardMode }
    }

    LaunchedEffect(Unit) {
        runCatching { refresh() }.onFailure { error = it.message }
    }

    Div(attrs = { classes("shell") }) {
        Aside(attrs = { classes("sidebar") }) {
            H1 { Text("LLM Gateway") }
            Button(attrs = {
                onClick {
                    selectedChatId = null
                    detail = null
                    selectedMode = InputGuardMode.REDACT
                    error = null
                }
            }) { Text("Новый чат") }
            chats.forEach { chat ->
                Button(attrs = {
                    classes("chat-link")
                    if (chat.chatId == selectedChatId) classes("selected")
                    onClick {
                        scope.launch {
                            runCatching { refresh(chat.chatId) }.onFailure { error = it.message }
                        }
                    }
                }) {
                    Text("${chat.chatId.take(8)} · ${chat.promptCount}/50 · ${chat.inputGuardMode}")
                }
            }
        }
        Main(attrs = { classes("main") }) {
            Header(attrs = { classes("toolbar") }) {
                Select(attrs = {
                    if (detail != null) disabled()
                    onChange { event -> event.value?.let { selectedMode = InputGuardMode.valueOf(it) } }
                }) {
                    Option(InputGuardMode.REDACT.name, attrs = {
                        if (selectedMode == InputGuardMode.REDACT) selected()
                    }) { Text("REDACT") }
                    Option(InputGuardMode.BLOCK.name, attrs = {
                        if (selectedMode == InputGuardMode.BLOCK) selected()
                    }) { Text("BLOCK") }
                }
                Button(attrs = {
                    classes("icon-button")
                    attr("aria-label", "Открыть debug-панель")
                    attr("data-icon", "Outlined.BugReport")
                    if (selectedChatId == null) disabled()
                    onClick {
                        selectedChatId?.let { chatId ->
                            window.open(
                                debugWindowUrl(chatId),
                                debugWindowName(chatId),
                                "popup,width=1100,height=800",
                            )?.focus()
                        }
                    }
                }) { BugReportIcon() }
                if (selectedChatId != null) {
                    Button(attrs = {
                        classes("danger")
                        onClick {
                            val chatId = selectedChatId ?: return@onClick
                            scope.launch {
                                runCatching {
                                    api.deleteChat(chatId)
                                    refresh(null)
                                }.onFailure { error = it.message }
                            }
                        }
                    }) { Text("Удалить") }
                }
            }
            error?.let { P(attrs = { classes("error") }) { Text(it) } }
            Div(attrs = { classes("history") }) {
                detail?.turns?.forEach { turn ->
                    Message("user", turn.input.original, turn.input.decision.name)
                    turn.output?.let { Message("assistant", it.original, turn.providerStatus) }
                }
            }
            Div(attrs = { classes("composer") }) {
                TextArea(value = prompt, attrs = {
                    placeholder("Введите prompt")
                    if (sending) disabled()
                    onInput { prompt = it.value }
                })
                Button(attrs = {
                    if (sending || prompt.isBlank()) disabled()
                    onClick {
                        val value = prompt
                        sending = true
                        error = null
                        scope.launch {
                            runCatching {
                                val response = api.send(
                                    ChatRequest(
                                        prompt = value,
                                        chatId = selectedChatId,
                                        inputGuardMode = if (selectedChatId == null) selectedMode else null,
                                    ),
                                )
                                prompt = ""
                                refresh(response.chatId)
                            }.onFailure { failure ->
                                error = failure.message
                                runCatching {
                                    val newest = api.getChats().chats.firstOrNull()?.chatId
                                    refresh(selectedChatId ?: newest)
                                }
                            }
                            sending = false
                        }
                    }
                }) { Text(if (sending) "Отправка…" else "Отправить") }
            }
        }
    }
}

@Composable
private fun DebugApp() {
    val api = remember { BrowserApi() }
    val chatId = remember { Regex("(?:^|&)chat_id=([^&]+)").find(window.location.search.removePrefix("?"))?.groupValues?.get(1) }
    var detail by remember { mutableStateOf<ChatDetailResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(chatId) {
        if (chatId == null) {
            error = "chat_id отсутствует"
            return@LaunchedEffect
        }
        while (true) {
            runCatching { api.getChat(chatId) }
                .onSuccess {
                    detail = it
                    error = null
                }
                .onFailure {
                    detail = null
                    error = it.message ?: "Chat недоступен"
                }
            delay(1_500)
        }
    }

    Main(attrs = { classes("debug-main") }) {
        H1 { Text("Modified history · readonly") }
        error?.let { P(attrs = { classes("error") }) { Text(it) } }
        detail?.turns?.forEach { turn ->
            DebugMessage("user", turn.input)
            turn.output?.let { DebugMessage("assistant", it) }
        }
    }
}

@Composable
private fun Message(role: String, text: String, status: String) {
    Article(attrs = { classes("message", role) }) {
        Small { Text("$role · $status") }
        Pre { Text(text) }
    }
}

@Composable
private fun DebugMessage(role: String, guardedText: GuardedText) {
    Article(attrs = { classes("message", role) }) {
        Small { Text("$role · ${guardedText.decision}") }
        Pre { Text(guardedText.modified) }
        guardedText.findings.forEach { finding ->
            Code { Text("${finding.ruleId}: ${finding.replacement} · fp=${finding.fingerprint}") }
        }
    }
}

@Composable
private fun BugReportIcon() {
    Span(attrs = {
        id("debug-bug-report-icon")
        attr("aria-hidden", "true")
    })
    LaunchedEffect(Unit) {
        document.getElementById("debug-bug-report-icon")?.innerHTML =
            "<svg viewBox=\"0 0 24 24\" width=\"24\" height=\"24\" aria-hidden=\"true\">" +
                "<path fill=\"currentColor\" d=\"$BUG_REPORT_OUTLINED_PATH\"></path></svg>"
    }
}

private class BrowserApi(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    suspend fun getChats(): ChatListResponse = get("/v1/chats")

    suspend fun getChat(chatId: String): ChatDetailResponse = get("/v1/chats/$chatId")

    suspend fun send(request: ChatRequest): ChatTurnResponse = request(
        url = "/v1/chat",
        method = "POST",
        body = json.encodeToString(request),
    )

    suspend fun deleteChat(chatId: String): DeleteChatResponse = request(
        url = "/v1/chats/$chatId",
        method = "DELETE",
    )

    private suspend inline fun <reified T> get(url: String): T = request(url, "GET")

    private suspend inline fun <reified T> request(url: String, method: String, body: String? = null): T {
        val headers = Headers()
        headers.append("Content-Type", "application/json")
        val response = window.fetch(url, RequestInit(method = method, headers = headers, body = body)).await()
        val text = response.text().await()
        if (!response.ok) {
            val apiError = runCatching { json.decodeFromString<ApiError>(text) }.getOrNull()
            error(apiError?.let { "${it.code}: ${it.message}" } ?: "HTTP ${response.status}")
        }
        return json.decodeFromString(text)
    }
}

private const val BUG_REPORT_OUTLINED_PATH =
    "M20 8h-2.81c-.45-.78-1.07-1.45-1.82-1.96L17 4.41 15.59 3l-2.17 2.17C12.96 5.06 12.49 5 12 5s-.96.06-1.41.17L8.41 3 7 4.41l1.62 1.63C7.88 6.55 7.26 7.22 6.81 8H4v2h2.09c-.05.33-.09.66-.09 1v1H4v2h2v1c0 .34.04.67.09 1H4v2h2.81c1.04 1.79 2.97 3 5.19 3s4.15-1.21 5.19-3H20v-2h-2.09c.05-.33.09-.66.09-1v-1h2v-2h-2v-1c0-.34-.04-.67-.09-1H20V8zm-4 4v3c0 .22-.03.47-.07.7l-.1.65-.37.65c-.72 1.24-2.04 2-3.46 2s-2.74-.77-3.46-2l-.37-.64-.1-.65C8.03 15.48 8 15.23 8 15v-4c0-.23.03-.48.07-.7l.1-.65.37-.65c.3-.52.72-.97 1.21-1.31l.57-.39.74-.18C11.37 7.04 11.69 7 12 7c.32 0 .63.04.95.12l.68.16.61.42c.5.34.91.78 1.21 1.31l.38.65.1.65c.04.22.07.47.07.69v1zM10 14h4v2h-4zm0-4h4v2h-4z"
