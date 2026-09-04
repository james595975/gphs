package kr.hs.gunpo.school.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsRequestLimiterTest {
    @Test
    fun `request starts sixty second cooldown`() {
        val status = smsRequestLimit("2026-09-04", 1, 100_000, 100_001, "2026-09-04")

        assertFalse(status.canRequest)
        assertEquals(4, status.remainingToday)
        assertEquals(60, status.retryAfterSeconds)
    }

    @Test
    fun `fifth daily request exhausts beta limit`() {
        val status = smsRequestLimit("2026-09-04", 5, 0, 100_000, "2026-09-04")

        assertFalse(status.canRequest)
        assertEquals(0, status.remainingToday)
        assertEquals(0, status.retryAfterSeconds)
    }

    @Test
    fun `new seoul date resets daily count`() {
        val status = smsRequestLimit("2026-09-03", 5, 0, 100_000, "2026-09-04")

        assertTrue(status.canRequest)
        assertEquals(5, status.remainingToday)
    }
}
