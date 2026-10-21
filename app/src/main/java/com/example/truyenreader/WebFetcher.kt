package com.example.truyenreader

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Phuong an du phong khi nguon chan Jsoup bang Cloudflare:
 * tai trang bang WebView that (co JS nhu trinh duyet), cho render xong
 * roi lay HTML da render de parse nhu binh thuong.
 * Luon chay tren main thread.
 */
object WebFetcher {

    @SuppressLint("SetJavaScriptEnabled")
    fun fetchHtml(context: Context, url: String, callback: (String?) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        main.post {
            val webView = WebView(context)
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
            var done = false
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                    // Cho Cloudflare / JS render xong
                    main.postDelayed({
                        if (!done) {
                            view?.evaluateJavascript(
                                "(function(){return document.documentElement.outerHTML;})()"
                            ) { result ->
                                if (!done) {
                                    done = true
                                    val html = decode(result)
                                    try { webView.destroy() } catch (e: Exception) { /* bo qua */ }
                                    callback(html)
                                }
                            }
                        }
                    }, 3000)
                }
            }
            // Timeout tong 25 giay
            main.postDelayed({
                if (!done) {
                    done = true
                    try { webView.stopLoading(); webView.destroy() } catch (e: Exception) { /* bo qua */ }
                    callback(null)
                }
            }, 25000)
            webView.loadUrl(url)
        }
    }

    private fun decode(jsonString: String?): String? {
        if (jsonString == null || jsonString == "null") return null
        return try {
            org.json.JSONTokener(jsonString).nextValue()?.toString()
        } catch (e: Exception) {
            jsonString.trim('"')
        }
    }
}
