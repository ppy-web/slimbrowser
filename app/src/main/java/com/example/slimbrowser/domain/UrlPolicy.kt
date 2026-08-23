package com.example.slimbrowser.domain

import java.net.IDN
import java.net.URI
import java.text.Normalizer
import java.util.Locale

object UrlPolicy {
    /**
     * Lenient navigation normalizer. Unlike [normalize], this preserves an explicitly supplied
     * URI scheme so the browser can either load it in WebView or hand it to a platform handler.
     * Bare host-like input still receives HTTPS for a convenient default.
     */
    fun normalizeForNavigation(input: String): String? {
        if (input.any(Char::isISOControl)) return null
        val candidate = input.trim()
        if (candidate.isEmpty()) return null

        normalize(candidate)?.let { return it }
        val withScheme = if (SCHEME_WITH_AUTHORITY.matchesAt(candidate, 0) ||
            EXPLICIT_SCHEME.matchesAt(candidate, 0)
        ) {
            candidate
        } else {
            "https://$candidate"
        }
        return runCatching {
            // Android's address bars conventionally accept spaces in a copied path. URI requires
            // them escaped, so repair them here instead of rejecting an otherwise usable address.
            URI(withScheme.replace(" ", "%20")).takeIf { it.scheme != null }?.toASCIIString()
        }.getOrNull()
    }

    fun normalize(input: String): String? {
        if (input.any(Char::isISOControl)) return null
        val candidate = input.trim()
        if (candidate.isEmpty()) return null

        val withScheme = if (SCHEME_WITH_AUTHORITY.matchesAt(candidate, 0)) candidate else "https://$candidate"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null

        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.isOpaque || uri.rawUserInfo != null) return null

        val authority = uri.rawAuthority ?: return null
        val (rawHost, port) = splitAuthority(authority) ?: return null
        if (port != -1 && port !in 1..65535) return null

        val normalizedHost = if (rawHost.contains(':')) {
            val words = IpAddressParser.parseIpv6(rawHost) ?: return null
            NormalizedHost(IpAddressParser.formatIpv6(words), isIpv6 = true)
        } else {
            val asciiHost = normalizeIdnHost(rawHost) ?: return null
            if (!isValidAsciiHost(asciiHost)) return null
            NormalizedHost(asciiHost, isIpv6 = false)
        }

        val normalized = buildString {
            append("https://")
            if (normalizedHost.isIpv6) append('[')
            append(normalizedHost.value)
            if (normalizedHost.isIpv6) append(']')
            if (port != -1) append(':').append(port)
            append(uri.rawPath.takeUnless { it.isNullOrEmpty() } ?: "/")
            if (uri.rawQuery != null) append('?').append(uri.rawQuery)
            if (uri.rawFragment != null) append('#').append(uri.rawFragment)
        }

        // A single-string URI parse preserves existing raw escapes while toASCIIString encodes Unicode once.
        return runCatching { URI(normalized).toASCIIString() }.getOrNull()
    }

    fun isAllowed(url: String): Boolean = normalizeForNavigation(url) != null

    /**
     * Compatibility entry point for the old single-site shell. A personal browser no longer
     * restricts navigation to [homeUrl]; callers should migrate to [NavigationPolicy].
     */
    @Suppress("UNUSED_PARAMETER")
    fun isAllowedNavigation(url: String, homeUrl: String): Boolean {
        return NavigationPolicy().decide(url, NavigationSource.LINK_CLICK) !is NavigationDecision.Block
    }

    /** Returns the normalized ASCII host, without IPv6 brackets. */
    fun hostOf(url: String): String? {
        val normalized = normalize(url) ?: return null
        val authority = runCatching { URI(normalized).rawAuthority }.getOrNull() ?: return null
        return splitAuthority(authority)?.first
    }

    private fun splitAuthority(authority: String): Pair<String, Int>? {
        if (authority.isBlank() || '@' in authority) return null
        if (authority.startsWith('[')) {
            val closingBracket = authority.indexOf(']')
            if (closingBracket <= 1) return null
            val host = authority.substring(1, closingBracket)
            val remainder = authority.substring(closingBracket + 1)
            val port = when {
                remainder.isEmpty() -> -1
                remainder.startsWith(':') && remainder.length > 1 -> {
                    val rawPort = remainder.substring(1)
                    if (rawPort.any { it !in '0'..'9' }) return null
                    rawPort.toIntOrNull() ?: return null
                }
                else -> return null
            }
            return host to port
        }

        if ('[' in authority || ']' in authority) return null
        if (authority.count { it == ':' } > 1) return null
        val separator = authority.lastIndexOf(':')
        if (separator < 0) return authority to -1
        val host = authority.substring(0, separator)
        val rawPort = authority.substring(separator + 1)
        if (rawPort.isEmpty() || rawPort.any { it !in '0'..'9' }) return null
        val port = rawPort.toIntOrNull() ?: return null
        return host to port
    }

    private fun isValidAsciiHost(host: String): Boolean {
        if (host.isEmpty()) return false
        if (IpAddressParser.parseIpv4(host) != null) return true
        if (IpAddressParser.looksLikeNonCanonicalIpv4(host)) return false
        val labels = host.split('.')
        if (labels.lastOrNull()?.all { it in '0'..'9' } == true) return false
        return host.length <= 253 && labels.all { label ->
            label.isNotEmpty() && label.length <= 63 &&
                label.first().isLetterOrDigit() && label.last().isLetterOrDigit() &&
                label.all { it.isAsciiLetterOrDigit() || it == '-' } &&
                isValidAceLabel(label)
        }
    }

    private fun normalizeIdnHost(rawHost: String): String? {
        val mappedDots = rawHost
            .replace('\u3002', '.')
            .replace('\uFF0E', '.')
            .replace('\uFF61', '.')
        val asciiHost = runCatching {
            IDN.toASCII(mappedDots, IDN.USE_STD3_ASCII_RULES)
        }.getOrNull()?.lowercase(Locale.ROOT)?.removeSingleTrailingDot() ?: return null

        // Java exposes IDNA2003. Reject compatibility mappings that silently change a label
        // beyond Unicode NFKC (for example faß -> fass) instead of producing an A-label.
        val unicodeLabels = mappedDots.removeSingleTrailingDot().split('.')
        val asciiLabels = asciiHost.split('.')
        if (unicodeLabels.size != asciiLabels.size) return null
        unicodeLabels.zip(asciiLabels).forEach { (unicodeLabel, asciiLabel) ->
            if (unicodeLabel.any { it.code > 0x7F } && !asciiLabel.startsWith("xn--")) {
                val compatibilityForm = Normalizer.normalize(unicodeLabel, Normalizer.Form.NFKC)
                    .lowercase(Locale.ROOT)
                if (compatibilityForm != asciiLabel) return null
            }
        }
        return asciiHost
    }

    private fun isValidAceLabel(label: String): Boolean {
        if (!label.startsWith("xn--", ignoreCase = true)) return true
        val unicode = runCatching { IDN.toUnicode(label) }.getOrNull() ?: return false
        if (unicode.equals(label, ignoreCase = true)) return false
        val roundTrip = runCatching {
            IDN.toASCII(unicode, IDN.USE_STD3_ASCII_RULES)
        }.getOrNull() ?: return false
        return roundTrip.equals(label, ignoreCase = true)
    }

    private fun String.removeSingleTrailingDot(): String =
        if (endsWith('.')) dropLast(1) else this

    private fun Char.isAsciiLetterOrDigit(): Boolean =
        this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

    private data class NormalizedHost(
        val value: String,
        val isIpv6: Boolean,
    )

    private val SCHEME_WITH_AUTHORITY = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")
    private val EXPLICIT_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
}
