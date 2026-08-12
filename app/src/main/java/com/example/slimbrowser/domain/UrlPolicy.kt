package com.example.slimbrowser.domain

import java.net.IDN
import java.net.URI

object UrlPolicy {
    fun normalize(input: String): String? {
        val candidate = input.trim()
        if (candidate.isEmpty() || candidate.any { it.isISOControl() }) return null

        val withScheme = if (candidate.contains("://")) candidate else "https://$candidate"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null

        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.rawUserInfo != null) return null

        val authority = uri.rawAuthority ?: return null
        val (rawHost, port) = splitAuthority(authority) ?: return null
        if (port !in listOf(-1, 443)) return null

        val asciiHost = if (rawHost.contains(':')) {
            rawHost.lowercase()
        } else {
            runCatching { IDN.toASCII(rawHost, IDN.USE_STD3_ASCII_RULES) }.getOrNull()
                ?.lowercase()
                ?: return null
        }
        if (!isValidHost(asciiHost)) return null

        return runCatching {
            URI(
                "https",
                null,
                asciiHost,
                port,
                uri.rawPath.takeUnless { it.isNullOrEmpty() } ?: "/",
                uri.rawQuery,
                uri.rawFragment,
            ).toASCIIString()
        }.getOrNull()
    }

    fun isAllowed(url: String): Boolean = normalize(url) != null

    private fun splitAuthority(authority: String): Pair<String, Int>? {
        if (authority.isBlank() || '@' in authority) return null
        if (authority.startsWith('[')) {
            val closingBracket = authority.indexOf(']')
            if (closingBracket <= 1) return null
            val host = authority.substring(1, closingBracket)
            val remainder = authority.substring(closingBracket + 1)
            val port = when {
                remainder.isEmpty() -> -1
                remainder.startsWith(':') && remainder.length > 1 ->
                    remainder.substring(1).toIntOrNull() ?: return null
                else -> return null
            }
            return host to port
        }

        if (authority.count { it == ':' } > 1) return null
        val separator = authority.lastIndexOf(':')
        if (separator < 0) return authority to -1
        val host = authority.substring(0, separator)
        val port = authority.substring(separator + 1).toIntOrNull() ?: return null
        return host to port
    }

    private fun isValidHost(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".localhost")) return false
        if (host.contains(':')) {
            return host.matches(Regex("^[0-9a-f:]+$")) && host.count { it == ':' } >= 2
        }
        if (host.matches(Regex("^(?:\\d{1,3}\\.){3}\\d{1,3}$"))) {
            return host.split('.').all { it.toIntOrNull() in 0..255 }
        }
        if (!host.contains('.')) return false
        return host.length <= 253 && host.split('.').all { label ->
            label.isNotEmpty() && label.length <= 63 &&
                label.first().isLetterOrDigit() && label.last().isLetterOrDigit() &&
                label.all { it.isLetterOrDigit() || it == '-' }
        }
    }
}
