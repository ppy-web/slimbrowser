package com.example.slimbrowser.domain

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val QUERY_PLACEHOLDER = "{query}"

private fun String.countOccurrences(value: String): Int {
    var count = 0
    var start = 0
    while (true) {
        val match = indexOf(value, start)
        if (match < 0) return count
        count++
        start = match + value.length
    }
}

/** Built-in search providers. Every template is HTTPS and receives an encoded query. */
enum class SearchEngine(
    val id: String,
    val displayName: String,
    private val queryUrlTemplate: String,
) {
    BAIDU(
        id = "baidu",
        displayName = "百度",
        queryUrlTemplate = "https://www.baidu.com/s?wd={query}",
    ),
    BING(
        id = "bing",
        displayName = "Bing",
        queryUrlTemplate = "https://www.bing.com/search?q={query}",
    ),
    DUCKDUCKGO(
        id = "duckduckgo",
        displayName = "DuckDuckGo",
        queryUrlTemplate = "https://duckduckgo.com/?q={query}",
    ),
    ;

    init {
        require(queryUrlTemplate.startsWith("https://"))
        require(queryUrlTemplate.countOccurrences(QUERY_PLACEHOLDER) == 1)
    }

    fun searchUrl(query: String): String {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val result = queryUrlTemplate.replace(QUERY_PLACEHOLDER, encoded)
        return checkNotNull(UrlPolicy.normalize(result)) { "Invalid built-in search template: $id" }
    }

    fun buildSearchUrl(query: String): String = searchUrl(query)

    companion object {
        val DEFAULT: SearchEngine = BAIDU

        fun fromId(id: String?): SearchEngine =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
    }
}
