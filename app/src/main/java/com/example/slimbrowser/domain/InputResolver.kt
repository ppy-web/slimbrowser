package com.example.slimbrowser.domain


sealed interface InputResolution {
    data class Url(val normalized: String) : InputResolution

    data class Search(
        val url: String,
        val query: String,
    ) : InputResolution

    data class Invalid(val reason: InputError) : InputResolution
}

sealed interface InputError {
    data object Empty : InputError
    data object ContainsControlCharacters : InputError
    data object InvalidUrl : InputError
}

/** Resolves the shared home/address-bar input without depending on Android APIs. */
object InputResolver {
    fun resolve(
        input: String,
        engine: SearchEngine = SearchEngine.DEFAULT,
    ): InputResolution {
        if (input.any(Char::isISOControl)) {
            return InputResolution.Invalid(InputError.ContainsControlCharacters)
        }

        val candidate = input.trim()
        if (candidate.isEmpty()) return InputResolution.Invalid(InputError.Empty)

        val scheme = EXPLICIT_SCHEME.find(candidate)?.groupValues?.get(1)
        if (scheme != null && !looksLikeHostAndPort(candidate)) return candidate.asUrlResolution()

        if (looksLikeUrl(candidate)) return candidate.asUrlResolution()

        return InputResolution.Search(
            url = engine.searchUrl(candidate),
            query = candidate,
        )
    }

    private fun String.asUrlResolution(): InputResolution =
        UrlPolicy.normalizeForNavigation(this)
            ?.let(InputResolution::Url)
            ?: InputResolution.Invalid(InputError.InvalidUrl)

    private fun looksLikeUrl(value: String): Boolean {
        if (value.any(Char::isWhitespace)) return false
        if (value.startsWith('[') || value.startsWith("www.", ignoreCase = true)) return true

        val authorityEnd = value.indexOfFirst { it == '/' || it == '?' || it == '#' }
            .let { if (it < 0) value.length else it }
        val authority = value.substring(0, authorityEnd)
        if ('@' in authority || ':' in authority) return true
        if (authority.equals("localhost", ignoreCase = true) ||
            authority.endsWith(".localhost", ignoreCase = true)
        ) {
            return true
        }
        if (authority.any { it == '.' || it == '\u3002' || it == '\uFF0E' || it == '\uFF61' }) {
            return true
        }
        return authorityEnd < value.length && value[authorityEnd] == '/'
    }

    private fun looksLikeHostAndPort(value: String): Boolean {
        val colon = value.indexOf(':')
        if (colon <= 0) return false
        val portEnd = value.indexOfFirst { it == '/' || it == '?' || it == '#' }
            .let { if (it < 0) value.length else it }
        if (portEnd <= colon + 1) return false
        return value.substring(colon + 1, portEnd).all { it in '0'..'9' }
    }

    private val EXPLICIT_SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.-]*):")
}
