package com.example.slimbrowser.domain

object BrowserErrorClassifier {
    fun fromDescription(description: String): BrowserError = when {
        description.contains("超时") -> BrowserError.Timeout
        description.contains("解析") || description.contains("找不到") -> BrowserError.Dns
        description.contains("证书") || description.contains("TLS") -> BrowserError.TlsBlocked
        description.contains("安全风险") -> BrowserError.SafeBrowsingBlocked
        description.contains("HTTP ") -> Regex("HTTP\\s+(\\d{3})").find(description)?.groupValues?.getOrNull(1)
            ?.toIntOrNull()?.let(BrowserError::Http) ?: BrowserError.Unknown(description)
        description.contains("渲染进程") -> BrowserError.RendererCrashed
        description.contains("网络") || description.contains("连接") -> BrowserError.Offline
        else -> BrowserError.Unknown(description)
    }
}
