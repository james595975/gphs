package kr.hs.gunpo.school.data

import android.content.Context
import java.time.Instant
import java.time.ZoneId

data class SmsRequestLimit(
    val canRequest: Boolean,
    val remainingToday: Int,
    val retryAfterSeconds: Int,
)

internal fun smsRequestLimit(
    storedDate: String?,
    storedCount: Int,
    lastRequestAt: Long,
    nowMillis: Long,
    today: String,
): SmsRequestLimit {
    val count = if (storedDate == today) storedCount.coerceAtLeast(0) else 0
    val retryMillis = (lastRequestAt + SmsRequestLimiter.COOLDOWN_MILLIS - nowMillis).coerceAtLeast(0)
    val retrySeconds = ((retryMillis + 999) / 1_000).toInt()
    return SmsRequestLimit(
        canRequest = count < SmsRequestLimiter.MAX_REQUESTS_PER_DAY && retrySeconds == 0,
        remainingToday = (SmsRequestLimiter.MAX_REQUESTS_PER_DAY - count).coerceAtLeast(0),
        retryAfterSeconds = retrySeconds,
    )
}

class SmsRequestLimiter(context: Context) {
    private val preferences = context.getSharedPreferences("sms_request_limit", Context.MODE_PRIVATE)

    @Synchronized
    fun status(nowMillis: Long = System.currentTimeMillis()): SmsRequestLimit {
        val today = seoulDate(nowMillis)
        return smsRequestLimit(
            storedDate = preferences.getString(KEY_DATE, null),
            storedCount = preferences.getInt(KEY_COUNT, 0),
            lastRequestAt = preferences.getLong(KEY_LAST_REQUEST_AT, 0),
            nowMillis = nowMillis,
            today = today,
        )
    }

    @Synchronized
    fun recordRequest(nowMillis: Long = System.currentTimeMillis()): SmsRequestLimit {
        val current = status(nowMillis)
        check(current.canRequest) { "SMS 요청 제한을 초과했습니다." }
        val today = seoulDate(nowMillis)
        val previousCount = if (preferences.getString(KEY_DATE, null) == today) {
            preferences.getInt(KEY_COUNT, 0)
        } else {
            0
        }
        preferences.edit()
            .putString(KEY_DATE, today)
            .putInt(KEY_COUNT, previousCount + 1)
            .putLong(KEY_LAST_REQUEST_AT, nowMillis)
            .apply()
        return status(nowMillis)
    }

    private fun seoulDate(nowMillis: Long): String =
        Instant.ofEpochMilli(nowMillis).atZone(SEOUL_ZONE).toLocalDate().toString()

    companion object {
        const val MAX_REQUESTS_PER_DAY = 5
        const val COOLDOWN_MILLIS = 60_000L
        private val SEOUL_ZONE = ZoneId.of("Asia/Seoul")
        private const val KEY_DATE = "date"
        private const val KEY_COUNT = "count"
        private const val KEY_LAST_REQUEST_AT = "last_request_at"
    }
}
