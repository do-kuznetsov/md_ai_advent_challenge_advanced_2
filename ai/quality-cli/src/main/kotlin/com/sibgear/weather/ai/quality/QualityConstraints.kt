package com.sibgear.weather.ai.quality

internal object QualityConstraints {

    private val riskOrder = mapOf(
        RiskLevel.LOW to LOW_RISK_ORDER,
        RiskLevel.UNKNOWN to UNKNOWN_RISK_ORDER,
        RiskLevel.MEDIUM to MEDIUM_RISK_ORDER,
        RiskLevel.HIGH to HIGH_RISK_ORDER,
    )

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun validate(candidate: AssessmentEnvelope, input: ProductInput): List<String> = buildList {
        if (candidate.confidenceScore !in 0.0..1.0) {
            add("confidence_score must be in 0..1")
        }
        addAll(validate(candidate.answer, input))
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun validate(candidate: ProductSafetyAssessment, input: ProductInput): List<String> = buildList {
        if (candidate.safeSummary.isBlank()) {
            add("safe_summary must not be blank")
        }

        val matched = candidate.matchedAdditives
        if (matched.isEmpty()) {
            if (candidate.riskLevel !in setOf(RiskLevel.LOW, RiskLevel.UNKNOWN)) {
                add("empty matched_additives cannot have risky risk_level")
            }
            if (candidate.warnings.isNotEmpty()) {
                add("empty matched_additives must have empty warnings")
            }
        }
        if (matched.isNotEmpty() && candidate.riskLevel != aggregateRisk(matched)) {
            add("risk_level must equal the maximum matched additive risk")
        }
        if (
            matched.isNotEmpty() &&
            candidate.riskLevel != RiskLevel.LOW &&
            candidate.warnings.isEmpty()
        ) {
            add("risky assessment must contain warnings")
        }
        if (candidate.warnings.any(String::isBlank)) {
            add("warnings must not contain blank items")
        }

        val reference = input.referenceAdditives.associateBy(::additiveKey)
        val candidateKeys = matched.map(::additiveKey)
        if (candidateKeys.toSet().size != candidateKeys.size) {
            add("matched_additives contains duplicate additive")
        }
        if (candidateKeys.toSet() != reference.keys) {
            add("matched_additives must exactly match reference_additives")
        }
        matched.forEach { additive ->
            if (
                additive.matchedText.isBlank() ||
                additive.canonicalName.isBlank() ||
                additive.reason.isBlank()
            ) {
                add("matched additive fields must not be blank")
            }
            val source = reference[additiveKey(additive)]
            if (source == null) {
                add("matched additive is absent from reference_additives")
            } else {
                if (source.canonicalName != additive.canonicalName) {
                    add("matched additive canonical_name differs from reference")
                }
                if (source.riskLevel != additive.riskLevel) {
                    add("matched additive risk_level differs from reference")
                }
            }
        }
    }

    private fun aggregateRisk(additives: List<MatchedAdditive>): RiskLevel =
        additives.maxBy { additive -> riskOrder.getValue(additive.riskLevel) }.riskLevel

    private fun additiveKey(additive: ReferenceAdditive): String =
        additive.code?.uppercase() ?: additive.matchedText.lowercase()

    private fun additiveKey(additive: MatchedAdditive): String =
        additive.code?.uppercase() ?: additive.matchedText.lowercase()

    private const val LOW_RISK_ORDER = 0
    private const val UNKNOWN_RISK_ORDER = 1
    private const val MEDIUM_RISK_ORDER = 2
    private const val HIGH_RISK_ORDER = 3
}
