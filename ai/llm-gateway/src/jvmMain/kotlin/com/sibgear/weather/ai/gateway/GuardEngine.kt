package com.sibgear.weather.ai.gateway

import java.net.Inet4Address
import java.net.InetAddress
import java.net.URI
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Base64

internal class GuardEngine {

    fun inspectInput(text: String, mode: InputGuardMode): GuardedText {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
        val findings = secretFindings(normalized, "INPUT")
        val decision = when {
            findings.isEmpty() -> GuardDecision.ALLOW
            mode == InputGuardMode.BLOCK -> GuardDecision.BLOCK
            else -> GuardDecision.REDACT
        }
        return GuardedText(
            original = text,
            modified = if (findings.isEmpty()) normalized else redact(normalized, findings),
            decision = decision,
            findings = findings,
        )
    }

    fun inspectOutput(text: String, systemCanary: String): GuardedText {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
        val systemPromptLeak = normalized.contains(systemCanary) || SYSTEM_PROMPT_PATTERN.containsMatchIn(normalized)
        if (systemPromptLeak) {
            val leakedValue = SYSTEM_PROMPT_PATTERN.find(normalized)?.value ?: systemCanary
            val finding = finding(
                ruleId = if (normalized.contains(systemCanary)) "OUTPUT_SYSTEM_CANARY" else "OUTPUT_SYSTEM_PROMPT_PATTERN",
                category = "SYSTEM_PROMPT",
                start = 0,
                end = normalized.length,
                replacement = "[REDACTED_SYSTEM_PROMPT_LEAK]",
                value = leakedValue,
            )
            return GuardedText(text, finding.replacement, GuardDecision.REDACT, listOf(finding))
        }

        val findings = nonOverlapping(
            secretFindings(normalized, "OUTPUT") + suspiciousUrlFindings(normalized) + dangerousCommandFindings(normalized),
        )
        return GuardedText(
            original = text,
            modified = redact(normalized, findings),
            decision = if (findings.isEmpty()) GuardDecision.ALLOW else GuardDecision.REDACT,
            findings = findings,
        )
    }

    private fun secretFindings(text: String, rulePrefix: String): List<GuardFinding> {
        val findings = mutableListOf<GuardFinding>()
        collect(text, SPLIT_OPENAI_KEY, "${rulePrefix}_SPLIT_API_KEY", "API_KEY", "[REDACTED_API_KEY]", findings)
        collect(text, OPENAI_KEY, "${rulePrefix}_OPENAI_KEY", "API_KEY", "[REDACTED_API_KEY]", findings)
        collect(text, GITHUB_KEY, "${rulePrefix}_GITHUB_KEY", "API_KEY", "[REDACTED_API_KEY]", findings)
        collect(text, AWS_KEY, "${rulePrefix}_AWS_KEY", "API_KEY", "[REDACTED_API_KEY]", findings)
        collect(text, EMAIL, "${rulePrefix}_EMAIL", "EMAIL", "[REDACTED_EMAIL]", findings)
        collect(text, PHONE, "${rulePrefix}_PHONE", "PHONE", "[REDACTED_PHONE]", findings)

        CARD.findAll(text).forEach { match ->
            val digits = match.value.filter(Char::isDigit)
            if (digits.length in 13..19 && luhnValid(digits)) {
                findings += finding(
                    "${rulePrefix}_CARD_LUHN",
                    "PAYMENT_CARD",
                    match.range.first,
                    match.range.last + 1,
                    "[REDACTED_CARD]",
                    match.value,
                )
            }
        }

        BASE64.findAll(text).forEach { match ->
            val decoded = decodeBase64(match.value) ?: return@forEach
            if (directSecretFindings(decoded).isNotEmpty()) {
                findings += finding(
                    "${rulePrefix}_BASE64_SECRET",
                    "ENCODED_SECRET",
                    match.range.first,
                    match.range.last + 1,
                    "[REDACTED_ENCODED_SECRET]",
                    match.value,
                )
            }
        }
        return nonOverlapping(findings)
    }

    private fun directSecretFindings(text: String): List<GuardFinding> {
        val findings = mutableListOf<GuardFinding>()
        collect(text, OPENAI_KEY, "INPUT_OPENAI_KEY", "API_KEY", "[REDACTED_API_KEY]", findings)
        collect(text, GITHUB_KEY, "INPUT_GITHUB_KEY", "API_KEY", "[REDACTED_API_KEY]", findings)
        collect(text, AWS_KEY, "INPUT_AWS_KEY", "API_KEY", "[REDACTED_API_KEY]", findings)
        collect(text, EMAIL, "INPUT_EMAIL", "EMAIL", "[REDACTED_EMAIL]", findings)
        return findings
    }

    private fun suspiciousUrlFindings(text: String): List<GuardFinding> =
        URL.findAll(text).mapNotNull { match ->
            val value = match.value.trimEnd('.', ',', ';', ')', ']', '}')
            if (!isSuspiciousUrl(value)) return@mapNotNull null
            finding(
                "OUTPUT_SUSPICIOUS_URL",
                "SUSPICIOUS_URL",
                match.range.first,
                match.range.first + value.length,
                "[REDACTED_SUSPICIOUS_URL]",
                value,
            )
        }.toList()

    private fun dangerousCommandFindings(text: String): List<GuardFinding> {
        val findings = mutableListOf<GuardFinding>()
        collect(text, PIPE_TO_SHELL, "OUTPUT_PIPE_TO_SHELL", "DANGEROUS_COMMAND", "[REDACTED_COMMAND]", findings)
        collect(text, DESTRUCTIVE_COMMAND, "OUTPUT_DESTRUCTIVE_COMMAND", "DANGEROUS_COMMAND", "[REDACTED_COMMAND]", findings)
        collect(text, REVERSE_SHELL, "OUTPUT_REVERSE_SHELL", "DANGEROUS_COMMAND", "[REDACTED_COMMAND]", findings)
        collect(text, EXFILTRATION, "OUTPUT_EXFILTRATION", "DANGEROUS_COMMAND", "[REDACTED_COMMAND]", findings)
        return findings
    }

    private fun isSuspiciousUrl(value: String): Boolean {
        val lower = value.lowercase()
        if (lower.startsWith("javascript:") || lower.startsWith("data:")) return true
        val uri = runCatching { URI(value) }.getOrNull() ?: return true
        if (uri.scheme != "https" || uri.userInfo != null) return true
        val host = uri.host?.lowercase() ?: return true
        if (host == "localhost" || host.endsWith(".localhost") || host.split('.').any { it.startsWith("xn--") }) return true
        val address = host.takeIf { candidate ->
            candidate.contains(':') || candidate.all { it.isDigit() || it == '.' }
        }?.let { runCatching { InetAddress.getByName(it) }.getOrNull() }
        return address != null && isPrivate(address)
    }

    private fun isPrivate(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress) {
            return true
        }
        if (address is Inet4Address) {
            val bytes = address.address.map(Byte::toInt).map { it and 0xff }
            return bytes[0] == 100 && bytes[1] in 64..127 || bytes[0] == 169 && bytes[1] == 254
        }
        return false
    }

    private fun collect(
        text: String,
        regex: Regex,
        ruleId: String,
        category: String,
        replacement: String,
        target: MutableList<GuardFinding>,
    ) {
        regex.findAll(text).forEach { match ->
            target += finding(ruleId, category, match.range.first, match.range.last + 1, replacement, match.value)
        }
    }

    private fun finding(
        ruleId: String,
        category: String,
        start: Int,
        end: Int,
        replacement: String,
        value: String,
    ): GuardFinding = GuardFinding(
        ruleId = ruleId,
        category = category,
        start = start,
        end = end,
        replacement = replacement,
        fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .take(6)
            .joinToString("") { "%02x".format(it) },
    )

    private fun nonOverlapping(findings: List<GuardFinding>): List<GuardFinding> {
        val selected = mutableListOf<GuardFinding>()
        findings.sortedWith(compareBy<GuardFinding> { it.start }.thenByDescending { it.end - it.start }).forEach { candidate ->
            if (selected.none { candidate.start < it.end && candidate.end > it.start }) selected += candidate
        }
        return selected.sortedBy(GuardFinding::start)
    }

    private fun redact(text: String, findings: List<GuardFinding>): String {
        var result = text
        findings.asReversed().forEach { finding ->
            result = result.replaceRange(finding.start, finding.end, finding.replacement)
        }
        return result
    }

    private fun decodeBase64(value: String): String? = runCatching {
        Base64.getDecoder().decode(value).toString(Charsets.UTF_8).takeIf { decoded -> decoded.all { !it.isISOControl() } }
    }.getOrNull()

    private fun luhnValid(digits: String): Boolean {
        var sum = 0
        var doubleDigit = false
        for (index in digits.indices.reversed()) {
            var digit = digits[index].digitToInt()
            if (doubleDigit) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
            doubleDigit = !doubleDigit
        }
        return sum % 10 == 0
    }

    private companion object {

        val OPENAI_KEY = Regex("\\bsk-(?:proj-)?[A-Za-z0-9_-]{6,}\\b")
        val GITHUB_KEY = Regex("\\bghp_[A-Za-z0-9]{8,}\\b")
        val AWS_KEY = Regex("\\bAKIA[0-9A-Z]{16}\\b")
        val EMAIL = Regex("\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b", RegexOption.IGNORE_CASE)
        val PHONE = Regex("(?<!\\w)(?:\\+\\d{10,15}|\\+?\\d{1,3}[ -]\\(?\\d{3}\\)?[ -]\\d{3}[ -]\\d{2}[ -]\\d{2})(?!\\w)")
        val CARD = Regex("(?<!\\d)(?:\\d[ -]?){12,18}\\d(?!\\d)")
        val BASE64 = Regex("(?<![A-Za-z0-9+/])[A-Za-z0-9+/]{16,4096}={0,2}(?![A-Za-z0-9+/])")
        val SPLIT_OPENAI_KEY = Regex("sk-[\\\"']?\\s*\\+\\s*[\\\"']?proj-[A-Za-z0-9_-]{3,}", RegexOption.IGNORE_CASE)
        val URL = Regex("(?i)(?:https?://|javascript:|data:)[^\\s<>()]+")
        val PIPE_TO_SHELL = Regex("(?i)\\b(?:curl|wget)\\b[^\\n|]{0,300}\\|\\s*(?:sh|bash|zsh)\\b")
        val DESTRUCTIVE_COMMAND = Regex("(?i)\\brm\\s+-[a-z]*r[a-z]*f[a-z]*\\s+(?:/|~|\\${'$'}HOME)(?=\\s|$)")
        val REVERSE_SHELL = Regex("(?i)(?:/dev/tcp/|\\bnc\\s+[^\\n]{0,100}\\s-e\\s|bash\\s+-i\\s+>&)")
        val EXFILTRATION = Regex("(?i)\\b(?:curl|wget)\\b[^\\n]{0,300}(?:--data|-d|--upload-file)\\b")
        val SYSTEM_PROMPT_PATTERN = Regex(
            "(?i)(?:system prompt|developer (?:message|instructions)|" +
                "you are a concise assistant behind a security gateway|never reveal this system prompt)",
        )
    }
}
