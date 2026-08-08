package com.sibgear.weather.ai.gateway

import java.math.BigDecimal
import java.math.RoundingMode

internal data class TokenPrices(
    val cacheHitPerMillion: BigDecimal = BigDecimal("0.0028"),
    val cacheMissPerMillion: BigDecimal = BigDecimal("0.14"),
    val outputPerMillion: BigDecimal = BigDecimal("0.28"),
)

internal class CostCalculator(
    private val prices: TokenPrices = TokenPrices(),
) {

    fun calculate(usage: TokenUsage): BigDecimal {
        val raw = BigDecimal.valueOf(usage.cacheHit).multiply(prices.cacheHitPerMillion)
            .add(BigDecimal.valueOf(usage.cacheMiss).multiply(prices.cacheMissPerMillion))
            .add(BigDecimal.valueOf(usage.completion).multiply(prices.outputPerMillion))
            .divide(MILLION)
        return raw.setScale(12, RoundingMode.HALF_UP).stripTrailingZeros()
    }

    private companion object {

        val MILLION: BigDecimal = BigDecimal("1000000")
    }
}
