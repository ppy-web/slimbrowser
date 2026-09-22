package com.example.slimbrowser.domain

/** Strict, allocation-light IP literal parsing shared by the URL and navigation policies. */
internal object IpAddressParser {
    fun parseIpv4(value: String): IntArray? {
        val parts = value.split('.')
        if (parts.size != IPV4_PART_COUNT) return null

        val octets = IntArray(IPV4_PART_COUNT)
        for (index in parts.indices) {
            val part = parts[index]
            if (part.isEmpty() || part.any { it !in '0'..'9' }) return null
            // RFC 3986 dec-octets do not permit ambiguous octal-style leading zeroes.
            if (part.length > 1 && part.first() == '0') return null
            val octet = part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            octets[index] = octet
        }
        return octets
    }

    fun parseIpv6(value: String): IntArray? {
        if (value.isEmpty() || '%' in value || value.contains(":::")) return null

        val compressionIndex = value.indexOf("::")
        if (compressionIndex >= 0 && value.indexOf("::", compressionIndex + 1) >= 0) return null

        val hasCompression = compressionIndex >= 0
        val leftText = if (hasCompression) value.substring(0, compressionIndex) else value
        val rightText = if (hasCompression) value.substring(compressionIndex + 2) else ""
        val leftTokens = splitIpv6Side(leftText) ?: return null
        val rightTokens = if (hasCompression) splitIpv6Side(rightText) ?: return null else emptyList()
        val allTokens = leftTokens + rightTokens
        if (hasCompression && rightTokens.isEmpty() && allTokens.any { '.' in it }) return null

        val explicitWords = ArrayList<Int>(IPV6_WORD_COUNT)
        allTokens.forEachIndexed { index, token ->
            if ('.' in token) {
                if (index != allTokens.lastIndex) return null
                val ipv4 = parseIpv4(token) ?: return null
                explicitWords += (ipv4[0] shl 8) or ipv4[1]
                explicitWords += (ipv4[2] shl 8) or ipv4[3]
            } else {
                if (token.length !in 1..4 || token.any { !it.isAsciiHexDigit() }) return null
                explicitWords += token.toInt(16)
            }
        }

        if (!hasCompression && explicitWords.size != IPV6_WORD_COUNT) return null
        if (hasCompression && explicitWords.size >= IPV6_WORD_COUNT) return null

        val result = IntArray(IPV6_WORD_COUNT)
        if (!hasCompression) {
            explicitWords.forEachIndexed { index, word -> result[index] = word }
            return result
        }

        val leftWordCount = countWords(leftTokens) ?: return null
        val rightWordCount = countWords(rightTokens) ?: return null
        val zeroWordCount = IPV6_WORD_COUNT - leftWordCount - rightWordCount
        if (zeroWordCount <= 0) return null

        var destination = 0
        parseTokensToWords(leftTokens)?.forEach { result[destination++] = it }
        destination += zeroWordCount
        parseTokensToWords(rightTokens)?.forEach { result[destination++] = it }
        return result.takeIf { destination == IPV6_WORD_COUNT }
    }

    /** Produces the stable compressed representation recommended by RFC 5952. */
    fun formatIpv6(words: IntArray): String {
        require(words.size == IPV6_WORD_COUNT)

        var bestStart = -1
        var bestLength = 0
        var index = 0
        while (index < words.size) {
            if (words[index] != 0) {
                index++
                continue
            }
            val start = index
            while (index < words.size && words[index] == 0) index++
            val length = index - start
            if (length >= 2 && length > bestLength) {
                bestStart = start
                bestLength = length
            }
        }

        return buildString {
            index = 0
            while (index < words.size) {
                if (index == bestStart) {
                    append("::")
                    index += bestLength
                    continue
                }
                if (isNotEmpty() && last() != ':') append(':')
                append(words[index].toString(16))
                index++
            }
        }
    }

    /** Rejects legacy decimal/octal/hex forms that URL parsers can reinterpret as IPv4. */
    fun looksLikeNonCanonicalIpv4(value: String): Boolean {
        if (value.isEmpty()) return false
        if (value.all { it in '0'..'9' || it == '.' }) return true
        return value.split('.').all { part ->
            part.isNotEmpty() && (
                part.all { it in '0'..'9' } ||
                    (part.startsWith("0x", ignoreCase = true) &&
                        part.length > 2 && part.drop(2).all { it.isAsciiHexDigit() })
                )
        }
    }

    private fun splitIpv6Side(value: String): List<String>? {
        if (value.isEmpty()) return emptyList()
        val tokens = value.split(':')
        return tokens.takeIf { tokens.none(String::isEmpty) }
    }

    private fun countWords(tokens: List<String>): Int? = parseTokensToWords(tokens)?.size

    private fun parseTokensToWords(tokens: List<String>): List<Int>? {
        val words = ArrayList<Int>(tokens.size)
        tokens.forEachIndexed { index, token ->
            if ('.' in token) {
                if (index != tokens.lastIndex) return null
                val ipv4 = parseIpv4(token) ?: return null
                words += (ipv4[0] shl 8) or ipv4[1]
                words += (ipv4[2] shl 8) or ipv4[3]
            } else {
                if (token.length !in 1..4 || token.any { !it.isAsciiHexDigit() }) return null
                words += token.toInt(16)
            }
        }
        return words
    }

    private const val IPV4_PART_COUNT = 4
    private const val IPV6_WORD_COUNT = 8

    private fun Char.isAsciiHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}
