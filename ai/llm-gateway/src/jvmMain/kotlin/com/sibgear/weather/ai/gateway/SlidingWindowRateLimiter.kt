package com.sibgear.weather.ai.gateway

import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

internal class SlidingWindowRateLimiter(
    private val limit: Int = 10,
    private val windowMillis: Long = 60_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val requests = ConcurrentHashMap<String, ArrayDeque<Long>>()

    fun tryAcquire(clientIp: String): Boolean {
        val now = clock()
        val queue = requests.computeIfAbsent(clientIp) { ArrayDeque() }
        synchronized(queue) {
            while (queue.isNotEmpty() && queue.first() <= now - windowMillis) queue.removeFirst()
            if (queue.size >= limit) return false
            queue.addLast(now)
            return true
        }
    }
}
