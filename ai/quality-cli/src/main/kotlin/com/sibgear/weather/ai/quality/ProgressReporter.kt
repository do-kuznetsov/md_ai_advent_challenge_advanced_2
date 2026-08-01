package com.sibgear.weather.ai.quality

import java.nio.file.Path

internal interface ProgressReporter {

    fun onRunStarted(config: CliConfig, casesCount: Int)

    fun onCaseStage(caseId: String, stage: String, reason: String? = null)

    fun onRunFinished(summary: QualitySummary, output: Path)
}

internal object ConsoleProgressReporter : ProgressReporter {

    override fun onRunStarted(config: CliConfig, casesCount: Int) {
        println(
            "stage=start mode=${config.mode.name.lowercase()} cases=$casesCount " +
                "small_model=${config.smallModel} large_model=${config.largeModel} " +
                "confidence_threshold=${config.confidenceThreshold}",
        )
    }

    override fun onCaseStage(caseId: String, stage: String, reason: String?) {
        println(
            buildString {
                append("case=$caseId stage=$stage")
                reason?.let {
                    append(" reason=")
                    append(it)
                }
            },
        )
    }

    override fun onRunFinished(summary: QualitySummary, output: Path) {
        println(
            "stage=summary accepted_on_small=${summary.acceptedOnSmall} " +
                "escalated_to_large=${summary.escalatedToLarge} " +
                "accepted_on_large=${summary.acceptedOnLarge} " +
                "rejected=${summary.routingRejected} latency_ms=${summary.totalLatencyMillis} " +
                "cost_usd=${summary.totalCostUsd} output=$output",
        )
    }
}
