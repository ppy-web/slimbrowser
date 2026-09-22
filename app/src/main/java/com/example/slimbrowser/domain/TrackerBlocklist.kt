package com.example.slimbrowser.domain

/** Small, explainable first-party tracker list; deliberately conservative. */
object TrackerBlocklist {
    private val domains = setOf(
        "doubleclick.net", "googlesyndication.com", "google-analytics.com",
        "connect.facebook.net", "ads.twitter.com", "analytics.tiktok.com",
    )

    fun shouldBlock(host: String?): Boolean {
        val value = host?.lowercase()?.trimEnd('.') ?: return false
        return domains.any { value == it || value.endsWith(".$it") }
    }
}
