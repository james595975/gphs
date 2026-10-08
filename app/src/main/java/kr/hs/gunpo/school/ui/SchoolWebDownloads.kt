package kr.hs.gunpo.school.ui

import android.app.DownloadManager
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.Toast
import org.json.JSONTokener
import org.json.JSONObject

internal fun isSchoolAttachment(uri: Uri): Boolean =
    uri.scheme == "https" && isSchoolHost(uri.host) &&
        uri.getQueryParameter("mode") == "download"

private fun isSchoolHost(host: String?): Boolean =
    host == "gunpo.hs.kr" || host?.endsWith(".gunpo.hs.kr") == true

internal fun downloadSchoolAttachment(
    view: WebView,
    url: String,
    userAgent: String? = null,
    disposition: String? = null,
    mimeType: String? = null,
) {
    val uri = Uri.parse(url)
    if (uri.scheme !in setOf("https", "http") || !isSchoolHost(uri.host)) {
        Toast.makeText(view.context, "학교 첨부파일 주소를 확인할 수 없습니다.", Toast.LENGTH_LONG).show()
        return
    }
    // School response filenames use a legacy Korean encoding. Prefer the visible
    // link text so the saved filename remains readable without exposing a JS bridge.
    val script = """
        (function() {
          var target = ${JSONObject.quote(url)};
          var links = document.querySelectorAll('a[href]');
          for (var i = 0; i < links.length; i++) {
            if (links[i].href === target) return links[i].textContent.trim();
          }
          return '';
        })();
    """.trimIndent()
    view.evaluateJavascript(script) { value ->
        val label = runCatching { JSONTokener(value).nextValue() as? String }.getOrNull().orEmpty()
        val guessed = URLUtil.guessFileName(url, disposition, mimeType)
        val suggested = label.ifBlank { guessed }
        val safe = suggested.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim(' ', '.')
            .ifBlank { "학교_첨부파일" }
        val extension = safe.substringAfterLast('.', "").takeIf { it.matches(Regex("[A-Za-z0-9]{1,10}")) }
            ?: guessed.substringAfterLast('.', "").takeIf { it.matches(Regex("[A-Za-z0-9]{1,10}")) }
        val stem = if (extension != null && safe.endsWith(".$extension")) safe.dropLast(extension.length + 1) else safe
        var bytes = 0
        val trimmed = buildString {
            for (point in stem.codePoints().toArray()) {
                val character = String(Character.toChars(point))
                bytes += character.toByteArray(Charsets.UTF_8).size
                if (bytes > 200) break
                append(character)
            }
        }
        val filename = "${trimmed}_${System.currentTimeMillis().toString(36)}" + (extension?.let { ".$it" } ?: "")
        runCatching {
            val request = DownloadManager.Request(uri)
                .setTitle(suggested)
                .setDescription("군포고등학교 첨부파일")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
            val agent = userAgent ?: view.settings.userAgentString
            if (agent.isNotBlank()) request.addRequestHeader("User-Agent", agent)
            view.url?.let { request.addRequestHeader("Referer", it) }
            CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let {
                request.addRequestHeader("Cookie", it)
            }
            // Octet-stream is generic on this school server; allow DownloadManager
            // to infer the file type from the extension instead of forcing it.
            mimeType?.takeIf { !it.equals("application/octet-stream", ignoreCase = true) && it.contains('/') }
                ?.let(request::setMimeType)
            view.context.getSystemService(DownloadManager::class.java).enqueue(request)
        }.onSuccess {
            Toast.makeText(view.context, "다운로드를 시작했습니다. 다운로드 폴더에서 확인하세요.", Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(view.context, "다운로드를 시작하지 못했습니다. 다시 시도해 주세요.", Toast.LENGTH_LONG).show()
        }
    }
}
