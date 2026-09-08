package kr.hs.gunpo.school.domain

import kr.hs.gunpo.school.data.AcademicEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HomeAcademicEventPolicyTest {
    private val today = LocalDate.of(2026, 9, 8)

    @Test
    fun `지필고사를 중요 일정으로 표시한다`() {
        assertTrue(
            HomeAcademicEventPolicy.isImportant(
                AcademicEvent(LocalDate.of(2026, 9, 30), title = "1차 지필고사"),
            ),
        )
    }

    @Test
    fun `명절은 표시하고 토요휴업일은 제외한다`() {
        assertTrue(HomeAcademicEventPolicy.isImportant(AcademicEvent(today, title = "추석")))
        assertFalse(HomeAcademicEventPolicy.isImportant(AcademicEvent(today, title = "토요휴업일")))
    }

    @Test
    fun `홈에는 가까운 중요 일정부터 세 개를 표시한다`() {
        val events = listOf(
            AcademicEvent(LocalDate.of(2026, 10, 20), title = "전국연합평가"),
            AcademicEvent(LocalDate.of(2026, 9, 30), title = "1차 지필고사"),
            AcademicEvent(LocalDate.of(2026, 9, 12), title = "토요휴업일"),
            AcademicEvent(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 25), "추석"),
            AcademicEvent(LocalDate.of(2026, 10, 5), title = "대체공휴일"),
        )

        assertEquals(
            listOf("추석", "1차 지필고사", "대체공휴일"),
            HomeAcademicEventPolicy.upcoming(events, today).map { it.title },
        )
    }
}
