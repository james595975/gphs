package kr.hs.gunpo.school.domain

import kr.hs.gunpo.school.data.Lesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
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
}
