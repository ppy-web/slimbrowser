package com.example.slimbrowser.domain

import java.net.URI
import java.util.Locale

sealed interface ExternalAction {
    val uri: String?
    val requiresConfirmation: Boolean

    /** Must be executed as a dial action, never as a direct-call action. */
    data class Dial(override val uri: String) : ExternalAction {
        override val requiresConfirmation: Boolean = false
    }

    /** Must be executed as a send-to action, without attachments or arbitrary intent extras. */
    data class SendEmail(override val uri: String) : ExternalAction {
        override val requiresConfirmation: Boolean = false
    }

    /** Must be executed as a send-to action. */
    data class SendSms(override val uri: String) : ExternalAction {
        override val requiresConfirmation: Boolean = false
    }

    data class OpenGeo(override val uri: String) : ExternalAction {
        override val requiresConfirmation: Boolean = true
    }

    data class OpenMarket(override val uri: String) : ExternalAction {
        override val requiresConfirmation: Boolean = true
    }

    /** A valid custom URI with no WebView implementation; delegated to a platform handler. */
    data class OpenUri(override val uri: String) : ExternalAction {
        override val requiresConfirmation: Boolean = false
    }

    data class Reject(val reason: ExternalLinkRejection) : ExternalAction {
        override val uri: String? = null
        override val requiresConfirmation: Boolean = false
    }
}

enum class ExternalLinkRejection {
    EMPTY,
    CONTROL_CHARACTER,
    MALFORMED_URI,
    MISSING_SCHEME,
    INVALID_TARGET,
}

/** Classifies external schemes into the only platform actions the UI may execute. */
object ExternalLinkPolicy {
    fun resolve(input: String): ExternalAction {
        if (input.any(Char::isISOControl)) {
            return ExternalAction.Reject(ExternalLinkRejection.CONTROL_CHARACTER)
        }

        val candidate = input.trim()
        if (candidate.isEmpty()) return ExternalAction.Reject(ExternalLinkRejection.EMPTY)
        if (candidate.length > MAX_URI_LENGTH) {
            return ExternalAction.Reject(ExternalLinkRejection.INVALID_TARGET)
        }

        val uri = runCatching { URI(candidate) }.getOrNull()
            ?: return ExternalAction.Reject(ExternalLinkRejection.MALFORMED_URI)
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
            ?: return ExternalAction.Reject(ExternalLinkRejection.MISSING_SCHEME)

        if (containsEncodedControl(candidate)) {
            return ExternalAction.Reject(ExternalLinkRejection.INVALID_TARGET)
        }

        val specialized = when (scheme) {
            TEL_SCHEME -> resolveTelephone(uri)
            MAILTO_SCHEME -> resolveEmail(uri)
            SMS_SCHEME -> resolveSms(uri)
            GEO_SCHEME -> resolveGeo(uri)
            MARKET_SCHEME -> resolveMarket(uri)
            else -> ExternalAction.OpenUri(asciiUri(candidate) ?: candidate)
        }
        return if (specialized is ExternalAction.Reject) {
            // A syntactically valid URI is still worth offering to the platform, even when it
            // does not satisfy one of the convenience parsers above.
            ExternalAction.OpenUri(asciiUri(candidate) ?: candidate)
        } else {
            specialized
        }
    }

    fun decide(input: String): ExternalAction = resolve(input)

    private fun resolveTelephone(uri: URI): ExternalAction {
        if (!uri.isOpaque || uri.rawFragment != null) return invalidTarget()
        val number = normalizePhoneNumber(uri.rawSchemeSpecificPart) ?: return invalidTarget()
        return ExternalAction.Dial("tel:$number")
    }

    private fun resolveEmail(uri: URI): ExternalAction {
        if (!uri.isOpaque || uri.rawFragment != null) return invalidTarget()
        val schemeSpecific = uri.rawSchemeSpecificPart
        val recipients = schemeSpecific.substringBefore('?')
        val query = schemeSpecific.substringAfter('?', missingDelimiterValue = "")
        val hasQuery = '?' in schemeSpecific

        if (recipients.isEmpty() || !recipients.split(',').all(::isValidMailbox)) return invalidTarget()
        if (hasQuery && !hasOnlyAllowedQueryKeys(query, EMAIL_QUERY_KEYS)) return invalidTarget()

        val canonical = asciiUri("mailto:$schemeSpecific") ?: return invalidTarget()
        return ExternalAction.SendEmail(canonical)
    }

    private fun resolveSms(uri: URI): ExternalAction {
        if (!uri.isOpaque || uri.rawFragment != null) return invalidTarget()
        val schemeSpecific = uri.rawSchemeSpecificPart
        val rawNumber = schemeSpecific.substringBefore('?')
        val query = schemeSpecific.substringAfter('?', missingDelimiterValue = "")
        val hasQuery = '?' in schemeSpecific
        val number = normalizePhoneNumber(rawNumber) ?: return invalidTarget()
        if (hasQuery && !hasOnlyAllowedQueryKeys(query, SMS_QUERY_KEYS)) return invalidTarget()

        val canonical = buildString {
            append("sms:").append(number)
            if (hasQuery) append('?').append(query)
        }
        return ExternalAction.SendSms(asciiUri(canonical) ?: return invalidTarget())
    }

    private fun resolveGeo(uri: URI): ExternalAction {
        if (!uri.isOpaque || uri.rawFragment != null) return invalidTarget()
        val coordinatePart = uri.rawSchemeSpecificPart.substringBefore('?').substringBefore(';')
        val coordinates = coordinatePart.split(',')
        if (coordinates.size !in 2..3) return invalidTarget()
        val latitude = coordinates[0].toDoubleOrNull()?.takeIf(Double::isFinite) ?: return invalidTarget()
        val longitude = coordinates[1].toDoubleOrNull()?.takeIf(Double::isFinite) ?: return invalidTarget()
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return invalidTarget()
        if (coordinates.size == 3 && coordinates[2].toDoubleOrNull()?.isFinite() != true) return invalidTarget()

        val canonical = asciiUri("geo:${uri.rawSchemeSpecificPart}") ?: return invalidTarget()
        return ExternalAction.OpenGeo(canonical)
    }

    private fun resolveMarket(uri: URI): ExternalAction {
        if (uri.rawFragment != null || uri.rawSchemeSpecificPart.isBlank()) return invalidTarget()
        if (uri.rawUserInfo != null || '@' in uri.rawSchemeSpecificPart) return invalidTarget()
        val canonical = asciiUri("market:${uri.rawSchemeSpecificPart}") ?: return invalidTarget()
        return ExternalAction.OpenMarket(canonical)
    }

    private fun normalizePhoneNumber(raw: String): String? {
        if (raw.isEmpty() || raw.length > MAX_PHONE_TEXT_LENGTH || '%' in raw) return null
        if (raw.any { it !in PHONE_CHARACTERS }) return null
        val normalized = raw.filterNot { it == '-' || it == '.' || it == '(' || it == ')' }
        if (normalized.isEmpty()) return null
        if (normalized.first() == '+') {
            if (normalized.length == 1 || normalized.drop(1).any { !it.isDigit() }) return null
        } else if (normalized.any { !it.isDigit() }) {
            return null
        }
        return normalized.takeIf { it.count(Char::isDigit) <= MAX_PHONE_DIGITS }
    }

    private fun isValidMailbox(value: String): Boolean {
        if (value.isEmpty() || value.any(Char::isWhitespace)) return false
        if (value.count { it == '@' } != 1) return false
        val local = value.substringBefore('@')
        val domain = value.substringAfter('@')
        if (local.isEmpty() || local.any { it == '/' || it == ':' }) return false
        return UrlPolicy.normalize("https://$domain") != null
    }

    private fun hasOnlyAllowedQueryKeys(query: String, allowedKeys: Set<String>): Boolean {
        if (query.isEmpty()) return false
        return query.split('&').all { parameter ->
            val key = parameter.substringBefore('=').lowercase(Locale.ROOT)
            key.isNotEmpty() && key in allowedKeys
        }
    }

    private fun containsEncodedControl(value: String): Boolean {
        var index = value.indexOf('%')
        while (index >= 0 && index + 2 < value.length) {
            val code = value.substring(index + 1, index + 3).toIntOrNull(16)
            if (code != null && (code in 0x00..0x1F || code == 0x7F)) return true
            index = value.indexOf('%', index + 1)
        }
        return false
    }

    private fun asciiUri(value: String): String? = runCatching { URI(value).toASCIIString() }.getOrNull()

    private fun invalidTarget() = ExternalAction.Reject(ExternalLinkRejection.INVALID_TARGET)

    private const val TEL_SCHEME = "tel"
    private const val MAILTO_SCHEME = "mailto"
    private const val SMS_SCHEME = "sms"
    private const val GEO_SCHEME = "geo"
    private const val MARKET_SCHEME = "market"
    private const val MAX_URI_LENGTH = 4_096
    private const val MAX_PHONE_TEXT_LENGTH = 64
    private const val MAX_PHONE_DIGITS = 32
    private val PHONE_CHARACTERS = ('0'..'9').toSet() + setOf('+', '-', '.', '(', ')')
    private val EMAIL_QUERY_KEYS = setOf("subject", "body", "cc", "bcc")
    private val SMS_QUERY_KEYS = setOf("body")
}
