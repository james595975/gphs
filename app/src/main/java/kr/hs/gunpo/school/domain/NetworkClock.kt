package kr.hs.gunpo.school.domain

import kr.hs.gunpo.school.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        val endpoint = BuildConfig.CLOUDFLARE_API_BASE_URL
            .ifBlank { "https://www.google.com/generate_204" }

        runCatching {
            val requestStartedAt = System.currentTimeMillis()
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.useCaches = false
                connection.responseCode
                val requestFinishedAt = System.currentTimeMillis()
                val serverTime = connection.getHeaderFieldDate("Date", -1L)
                check(serverTime > 0L) { "서버 Date 헤더가 없습니다." }
                offsetMillis = serverTime - ((requestStartedAt + requestFinishedAt) / 2L)
                true
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(false)
    }
}