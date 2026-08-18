package kr.hs.gunpo.school.notification

import kr.hs.gunpo.school.data.AcademicEvent
import kr.hs.gunpo.school.data.Lesson
import kr.hs.gunpo.school.data.NeisState
import kr.hs.gunpo.school.data.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class NotificationScheduleResolverTest {
    private val date = LocalDate.of(2026, 8, 18)
    private val settings = UserSettings(
        studentName = "학생",
        studentNumber = "20401",
        grade = 2,
        classNumber = 4,
    )

    @Test fun `서버에서 받은 현재 반 시간표를 사용한다`() {
        val remote = listOf(Lesson(1, "논리와 사고", 500, 550))
        val result = NotificationScheduleResolver.resolve(
            date,
            settings,
            NeisState(
                timetableByDate = mapOf(date to remote),
                grade = 2,
                classNumber = 4,
                loadedMonths = setOf(YearMonth.from(date)),
            ),
        )

        assertTrue(result.isAvailable)
        assertEquals("논리와 사고", result.lessons.first().subject)
    }

    @Test fun `동기화에 실패하면 빈 시간표로 확정하지 않는다`() {
        val result = NotificationScheduleResolver.resolve(
            date,
            settings,
            NeisState(errorMessage = "network error", grade = 2, classNumber = 4),
        )

        assertFalse(result.isAvailable)
        assertTrue(result.lessons.isEmpty())
    }

    @Test fun `동기화된 달에 일정이 없으면 수업 없음으로 판정한다`() {
        val result = NotificationScheduleResolver.resolve(
            date,
            settings,
            NeisState(
                grade = 2,
                classNumber = 4,
                loadedMonths = setOf(YearMonth.from(date)),
            ),
        )

        assertTrue(result.isAvailable)
        assertTrue(result.lessons.isEmpty())
    }

    @Test fun `공휴일에는 자습 시간표를 우선한다`() {
        val result = NotificationScheduleResolver.resolve(
            date,
            settings,
            NeisState(
                events = listOf(AcademicEvent(date, title = "임시공휴일", scope = "공휴일")),
                errorMessage = "network error",
                grade = 2,
                classNumber = 4,
            ),
        )

        assertTrue(result.isAvailable)
        assertEquals(7, result.lessons.size)
        assertTrue(result.lessons.all { it.subject == "자습" })
    }
}
