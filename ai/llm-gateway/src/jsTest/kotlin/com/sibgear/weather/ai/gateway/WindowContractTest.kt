package com.sibgear.weather.ai.gateway

import kotlin.test.Test
import kotlin.test.assertEquals

internal class WindowContractTest {

    @Test
    fun debugWindowContractIsStable() {
        assertEquals("/debug?chat_id=chat-42", debugWindowUrl("chat-42"))
        assertEquals("llm-gateway-debug-chat-42", debugWindowName("chat-42"))
    }
}
