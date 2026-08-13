package kr.hs.gunpo.school.data

import android.content.Context
import kr.hs.gunpo.school.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.security.MessageDigest
import java.net.HttpURLConnection
import java.net.URL

data class NoticeState(
    val notices: List<Notice> = emptyList(),
    val isLoading: Boolean = false,
    val isFromCache: Boolean = false,
    val errorMessage: String? = null,
)

object SchoolNoticeParser {
    private val datePattern = Regex("\\b(20\\d{2})[-.](\\d{2})[-.](\\d{2})\\b")
    private val outputDate = DateTimeFormatter.ofPattern("yyyy.MM.dd")

    fun parse(
        html: String,
        baseUrl: String,
        section: String,
        category: String,
        today: LocalDate,
    ): List<Notice> {
        val document = Jsoup.parse(html, baseUrl)
        return document.select("tr:has(a[href*=act=view])").mapNotNull { row ->
            val anchor = row.selectFirst("a[href*=act=view]") ?: return@mapNotNull null
            val dateMatch = datePattern.find(row.text()) ?: return@mapNotNull null
            val date = runCatching {
                LocalDate.of(
                    dateMatch.groupValues[1].toInt(),
                    dateMatch.groupValues[2].toInt(),
                    dateMatch.groupValues[3].toInt(),
                )
            }.getOrNull() ?: return@mapNotNull null
            val title = anchor.attr("title").trim().ifBlank { anchor.text().trim() }
            val url = anchor.absUrl("href")
            if (title.isBlank() || url.isBlank()) return@mapNotNull null
            Notice(
                category = category,
                section = section,
                title = title,
                dateLabel = date.format(outputDate),
                url = url,
                isNew = !date.isBefore(today.minusDays(7)),
            )
        }.distinctBy { it.url }.sortedByDescending { it.dateLabel }
    }
}

class SchoolNoticeRepository(context: Context) {
    private val preferences = context.getSharedPreferences("school_notice_cache", Context.MODE_PRIVATE)

    suspend fun load(forceRefresh: Boolean = false, today: LocalDate): NoticeState = withContext(Dispatchers.IO) {
        val cached = readCache()
        if (!forceRefresh && cached.isNotEmpty() && cacheIsFresh()) {
            return@withContext NoticeState(cached, isFromCache = true)
        }

        runCatching {
            val websiteNotices = fetchWebsite(today)
            val websiteFingerprint = fingerprint(websiteNotices)
            val server = readServerNotices()
            val result = when {
                server != null && server.first == websiteFingerprint -> server.second
                server != null -> requestServerSync(websiteFingerprint) ?: websiteNotices
                else -> websiteNotices
            }
            result.also {
                check(it.isNotEmpty()) { "학교 게시판에서 공지를 찾지 못했습니다." }
                writeCache(it)
            }
        }.fold(
            onSuccess = { NoticeState(it) },
            onFailure = { error ->
                val fallback = cached.ifEmpty { SchoolData.notices }
                NoticeState(
                    notices = fallback,
                    isFromCache = true,
                    errorMessage = error.message ?: "학교 홈페이지에 연결할 수 없습니다.",
                )
            },
        )
    }

    private fun fetchWebsite(today: LocalDate): List<Notice> = sources.flatMap { source ->
        val html = Jsoup.connect(source.url)
            .userAgent("GunpoSchoolAndroid/0.1 (+https://www.gunpo.hs.kr/)")
            .timeout(15_000)
            .get()
            .outerHtml()
        SchoolNoticeParser.parse(html, source.url, source.section, source.category, today)
    }.distinctBy { it.url }.sortedByDescending { it.dateLabel }

    private fun readServerNotices(): Pair<String, List<Notice>>? = runCatching {
        if (BuildConfig.CLOUDFLARE_API_BASE_URL.isBlank()) return null
        val json = requestJson("/v1/notices")
        val notices = decodeNotices(json.optJSONArray("items"))
        json.optString("fingerprint") to notices
    }.getOrNull()?.takeIf { it.first.isNotBlank() && it.second.isNotEmpty() }

    private fun requestServerSync(clientFingerprint: String): List<Notice>? = runCatching {
        val body = JSONObject().put("clientFingerprint", clientFingerprint).toString()
        decodeNotices(requestJson("/v1/notices/sync", "POST", body).optJSONArray("items"))
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun decodeNotices(value: JSONArray?): List<Notice> = buildList {
        if (value == null) return@buildList
        for (index in 0 until value.length()) {
            val item = value.optJSONObject(index) ?: continue
            val title = item.optString("title")
            val url = item.optString("url")
            if (title.isBlank() || url.isBlank()) continue
            add(
                Notice(
                    category = item.optString("category", "공지"),
                    section = item.optString("section", "공지사항"),
                    title = title,
                    dateLabel = item.optString("dateLabel"),
                    url = url,
                    isNew = item.optBoolean("isNew"),
                ),
            )
        }
    }.sortedByDescending { it.dateLabel }

    private fun requestJson(path: String, method: String = "GET", body: String? = null): JSONObject {
        val connection = URL("${BuildConfig.CLOUDFLARE_API_BASE_URL}$path").openConnection() as HttpURLConnection
        return connection.run {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "GunpoSchoolAndroid/0.2")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            }
            try {
                if (responseCode !in 200..299) error("학교 데이터 서버 HTTP $responseCode")
                JSONObject(inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            } finally {
                disconnect()
            }
        }
    }

    private fun fingerprint(notices: List<Notice>): String {
        val canonical = notices.joinToString("\n") { "${it.section}|${it.title}|${it.dateLabel}|${it.url}" }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun cacheIsFresh(): Boolean =
        System.currentTimeMillis() - preferences.getLong(KEY_SAVED_AT, 0L) < CACHE_DURATION_MS

    private fun writeCache(notices: List<Notice>) {
        val array = JSONArray()
        notices.forEach { notice ->
            array.put(JSONObject().apply {
                put("category", notice.category)
                put("section", notice.section)
                put("title", notice.title)
                put("dateLabel", notice.dateLabel)
                put("url", notice.url)
                put("isNew", notice.isNew)
            })
        }
        preferences.edit()
            .putString(KEY_NOTICES, array.toString())
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .apply()
    }

    private fun readCache(): List<Notice> = runCatching {
        val array = JSONArray(preferences.getString(KEY_NOTICES, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    Notice(
                        category = item.getString("category"),
                        section = item.getString("section"),
                        title = item.getString("title"),
                        dateLabel = item.getString("dateLabel"),
                        url = item.getString("url"),
                        isNew = item.optBoolean("isNew"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private data class Source(
        val section: String,
        val category: String,
        val url: String,
    )

    private companion object {
        const val KEY_NOTICES = "notices"
        const val KEY_SAVED_AT = "saved_at"
        const val CACHE_DURATION_MS = 30 * 60 * 1000L

        val sources = listOf(
            Source(
                section = "가정통신문",
                category = "가정",
                url = "https://www.gunpo.hs.kr/main.php?menugrp=060300&master=bbs&act=list&master_sid=65",
            ),
            Source(
                section = "공지사항",
                category = "공지",
                url = "https://www.gunpo.hs.kr/main.php?menugrp=060100&master=bbs&act=list&master_sid=63",
            ),
        )
    }
}
