package com.example.slimbrowser.ui.browser

import android.graphics.Color
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebSettings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import java.io.File

/** Local-only viewer for Web Archive files saved by the reading list. */
class OfflineArchiveActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val webView = WebView(this)
        webView.setBackgroundColor(Color.WHITE)
        webView.settings.apply {
            javaScriptEnabled = false
            allowFileAccess = true
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        setContentView(webView)

        val path = intent.getStringExtra(EXTRA_PATH).orEmpty()
        val file = File(path).canonicalFile
        val root = File(cacheDir, "shared/reading").canonicalFile
        if (!file.path.startsWith(root.path + File.separator) || !file.isFile) {
            finish()
            return
        }
        webView.loadUrl(file.toUri().toString())
    }

    companion object {
        const val EXTRA_PATH = "archive_path"
    }
}
