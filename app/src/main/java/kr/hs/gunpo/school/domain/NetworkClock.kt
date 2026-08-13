package kr.hs.gunpo.school.domain

import kr.hs.gunpo.school.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

object NetworkClock {
    private val koreaZone = ZoneId.of("Asia/Seoul")

    @Volatile
    private var offsetMillis: Long = 0L

    fun now(): LocalDateTime = Instant
        .ofEpochMilli(System.currentTimeMillis() + offsetMillis)
        .atZone(koreaZone)
        .toLocalDateTime()

    suspend fun synchronize(): Boolean = withContext(Dispatchers.IO) {
        val baseUrl = BuildConfig.CLOUDFLARE_API_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) return@withContext false

        synchronizeFromTimeApi(baseUrl) || synchronizeFromDateHeader(baseUrl)
    }

    private fun synchronizeFromTimeApi(baseUrl: String): Boolean = runCatching {
        val requestStartedAt = System.currentTimeMillis()
        val connection = URL("$baseUrl/v1/time").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            check(connection.responseCode in 200..299) { "시간 서버 HTTP ${connection.responseCode}" }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val requestFinishedAt = System.currentTimeMillis()
            val serverTime = JSONObject(body).getLong("epochMillis")
            offsetMillis = calculateOffsetMillis(serverTime, requestStartedAt, requestFinishedAt)
            true
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(false)

    private fun synchronizeFromDateHeader(baseUrl: String): Boolean = runCatching {
        val requestStartedAt = System.currentTimeMillis()
        val connection = URL("$baseUrl/health").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.useCaches = false
            check(connection.responseCode in 200..299) { "시간 서버 HTTP ${connection.responseCode}" }
            val requestFinishedAt = System.currentTimeMillis()
            val serverTime = connection.getHeaderFieldDate("Date", -1L)
            check(serverTime > 0L) { "시간 서버 Date 헤더가 없습니다." }
            offsetMillis = calculateOffsetMillis(serverTime, requestStartedAt, requestFinishedAt)
            true
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(false)
}

internal fun calculateOffsetMillis(serverEpochMillis: Long, requestStartedAt: Long, requestFinishedAt: Long): Long {
    require(requestFinishedAt >= requestStartedAt)
    return serverEpochMillis - (requestStartedAt + (requestFinishedAt - requestStartedAt) / 2L)
}
