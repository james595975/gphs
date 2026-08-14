package kr.hs.gunpo.school.domain

import kr.hs.gunpo.school.data.Lesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class SchoolTimelineTest {
    private val lessons = listOf(
        Lesson(1, "대수", 500, 550),
        Lesson(2, "영어", 560, 610),
    )

    @Test fun `수업 중에는 현재와 다음 수업을 돌려준다`() {
        val result = SchoolTimeline.moment(LocalDateTime.of(2026, 7, 14, 8, 30), lessons)
        assertTrue(result is SchoolMoment.InClass)
        result as SchoolMoment.InClass
        assertEquals("대수", result.lesson.subject)
        assertEquals("영어", result.next?.subject)
    }

    @Test fun `수업 사이에는 쉬는 시간 상태를 돌려준다`() {
        val result = SchoolTimeline.moment(LocalDateTime.of(2026, 7, 14, 9, 15), lessons)
        assertTrue(result is SchoolMoment.BetweenClasses)
    }

    @Test fun `마지막 수업 뒤에는 완료 상태를 돌려준다`() {
        val result = SchoolTimeline.moment(LocalDateTime.of(2026, 7, 14, 18, 0), lessons)
        assertTrue(result is SchoolMoment.Finished)
    }

    @Test fun `금요일 일과 전에는 오늘 시간표를 선택한다`() {
        val at = LocalDateTime.of(2026, 8, 14, 10, 0)

        assertEquals(LocalDate.of(2026, 8, 14), SchoolTimeline.defaultTimetableDate(at, lessons))
    }

    @Test fun `금요일 마지막 수업이 끝나면 다음 월요일을 선택한다`() {
        val at = LocalDateTime.of(2026, 8, 14, 10, 10)

        assertEquals(LocalDate.of(2026, 8, 17), SchoolTimeline.defaultTimetableDate(at, lessons))
    }

    @Test fun `금요일 8교시가 있으면 8교시 종료까지 기다린다`() {
        val fridayLessons = lessons + Lesson(8, "자습", 16 * 60 + 20, 17 * 60 + 10)

        assertEquals(
            LocalDate.of(2026, 8, 14),
            SchoolTimeline.defaultTimetableDate(LocalDateTime.of(2026, 8, 14, 17, 9), fridayLessons),
        )
        assertEquals(
            LocalDate.of(2026, 8, 17),
            SchoolTimeline.defaultTimetableDate(LocalDateTime.of(2026, 8, 14, 17, 10), fridayLessons),
        )
    }

    @Test fun `주말에는 다음 월요일을 선택한다`() {
        assertEquals(
            LocalDate.of(2026, 8, 17),
            SchoolTimeline.defaultTimetableDate(LocalDateTime.of(2026, 8, 15, 9, 0), emptyList()),
        )
        assertEquals(
            LocalDate.of(2026, 8, 17),
            SchoolTimeline.defaultTimetableDate(LocalDateTime.of(2026, 8, 16, 9, 0), emptyList()),
        )
    }
}
