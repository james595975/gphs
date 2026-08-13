package kr.hs.gunpo.school.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkClockTest {
    @Test
    fun `offset uses the midpoint of the network request`() {
        val offset = calculateOffsetMillis(
            serverEpochMillis = 10_250L,
            requestStartedAt = 10_000L,
            requestFinishedAt = 10_100L,
        )

        assertEquals(200L, offset)
    }
}
