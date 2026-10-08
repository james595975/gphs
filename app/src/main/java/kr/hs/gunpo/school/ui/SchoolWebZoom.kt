package kr.hs.gunpo.school.ui

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

internal object SchoolWebZoom {
    fun prepareHtml(html: String, baseUrl: String): String {
        val document = Jsoup.parse(html, baseUrl)
        document.select("meta[name=viewport]").remove()
        document.head().prependElement("meta")
            .attr("name", "viewport")
            .attr("content", "width=device-width, initial-scale=1, minimum-scale=0.25, maximum-scale=5, user-scalable=yes")
        return document.outerHtml()
    }

    fun intercept(request: WebResourceRequest, userAgent: String): WebResourceResponse? {
        val uri = request.url
        if (!request.isForMainFrame || request.method != "GET" || uri.scheme != "https" ||
            !(uri.host == "gunpo.hs.kr" || uri.host?.endsWith(".gunpo.hs.kr") == true) ||
            uri.getQueryParameter("act") != "view") return null
        return runCatching {
            val connection = URL(uri.toString()).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("User-Agent", userAgent)
                connection.setRequestProperty("Accept-Encoding", "identity")
                request.requestHeaders["Referer"]?.let { connection.setRequestProperty("Referer", it) }
                CookieManager.getInstance().getCookie(uri.toString())?.let {
                    connection.setRequestProperty("Cookie", it)
                }
                if (connection.responseCode != 200 || !connection.contentType.orEmpty().contains("text/html", true)) {
                    android.util.Log.w("GunpoWebZoom", "Notice HTML unavailable: ${connection.responseCode} ${connection.contentType}")
                    return null
                }
                connection.headerFields.filterKeys { it?.equals("Set-Cookie", true) == true }.values.flatten().forEach {
                    CookieManager.getInstance().setCookie(uri.toString(), it)
                }
                val charsetName = Regex("charset=([^;\\s]+)", RegexOption.IGNORE_CASE)
                    .find(connection.contentType.orEmpty())?.groupValues?.get(1)?.trim('\'', '"')
                val charset = charsetName?.let { runCatching { charset(it) }.getOrNull() } ?: Charsets.UTF_8
                val html = connection.inputStream.bufferedReader(charset).use { it.readText() }
                // Apply before the renderer reads viewport constraints. A later
                // DOM edit alone does not reliably unlock WebView's zoom bounds.
                val body = prepareHtml(html, uri.toString()).toByteArray(Charsets.UTF_8)
                android.util.Log.d("GunpoWebZoom", "Prepared zoomable notice HTML: ${body.size} bytes")
                val headers = connection.headerFields.entries.filter { (name, _) ->
                    name != null && name.lowercase() !in setOf("content-length", "content-encoding", "transfer-encoding", "content-type")
                }.associate { (name, values) -> name!! to values.joinToString(", ") }
                WebResourceResponse("text/html", "UTF-8", 200, "OK", headers, ByteArrayInputStream(body))
            } finally { connection.disconnect() }
        }.onFailure { android.util.Log.w("GunpoWebZoom", "Could not prepare notice zoom", it) }.getOrNull()
    }
}
