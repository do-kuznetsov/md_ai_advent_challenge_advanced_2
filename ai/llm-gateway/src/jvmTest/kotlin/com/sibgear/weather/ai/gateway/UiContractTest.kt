package com.sibgear.weather.ai.gateway

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class UiContractTest {

    private val source = Path.of("src/jsMain/kotlin/com/sibgear/weather/ai/gateway/Main.kt").readText()

    @Test
    fun debugButtonUsesOutlinedBugReportWithoutVisibleText() {
        val button = source.substringAfter("attr(\"data-icon\", \"Outlined.BugReport\")").substringBefore("if (selectedChatId != null)")

        assertTrue(source.contains("BUG_REPORT_OUTLINED_PATH"))
        assertTrue(button.contains("BugReportIcon()"))
        assertFalse(button.contains("Text("))
    }

    @Test
    fun debugButtonHasAccessibilityAndDisabledState() {
        assertTrue(source.contains("attr(\"aria-label\", \"Открыть debug-панель\")"))
        assertTrue(source.contains("if (selectedChatId == null) disabled()"))
    }

    @Test
    fun debugPopupUsesStableNamedWindowAndFocus() {
        assertTrue(source.contains("/debug?chat_id=${'$'}chatId"))
        assertTrue(source.contains("llm-gateway-debug-${'$'}chatId"))
        assertTrue(source.contains("popup,width=1100,height=800"))
        assertTrue(source.contains(")?.focus()"))
    }

    @Test
    fun mainShowsOriginalAndDebugShowsModified() {
        val main = source.substringAfter("private fun MainApp()").substringBefore("private fun DebugApp()")
        val debug = source.substringAfter("private fun DebugApp()").substringBefore("private fun Message(")

        assertTrue(main.contains("turn.input.original"))
        assertTrue(main.contains("it.original"))
        assertTrue(debug.contains("DebugMessage"))
        assertTrue(source.substringAfter("private fun DebugMessage").contains("guardedText.modified"))
    }

    @Test
    fun debugIsReadonlyAndPolls() {
        val debug = source.substringAfter("private fun DebugApp()").substringBefore("private fun Message(")

        assertFalse(debug.contains("TextArea"))
        assertFalse(debug.contains("deleteChat"))
        assertFalse(debug.contains("Select("))
        assertTrue(debug.contains("delay(1_500)"))
        assertTrue(debug.contains("error = it.message"))
    }
}
