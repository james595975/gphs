package kr.hs.gunpo.school.domain

import kr.hs.gunpo.school.data.AcademicEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AcademicSchedulePolicyTest {
    @Test
    fun `법정공휴일은 1교시부터 7교시까지 모두 자습이다`() {
        val date = LocalDate.of(2026, 10, 9)

        val result = AcademicSchedulePolicy.overrideFor(date, listOf(AcademicEvent(date, title = "한글날")))!!

        assertEquals(AcademicScheduleKind.HOLIDAY_SELF_STUDY, result.kind)
        assertEquals((1..7).toList(), result.lessons.map { it.period })
        assertTrue(result.lessons.all { it.subject == "자습" && it.room == "자습실" })
    }

    @Test
    fun `2026년부터 공휴일인 제헌절도 전체 자습이다`() {
        val date = LocalDate.of(2026, 7, 17)

        val result = AcademicSchedulePolicy.overrideFor(date, listOf(AcademicEvent(date, title = "제헌절", scope = "국경일")))!!

        assertEquals(AcademicScheduleKind.HOLIDAY_SELF_STUDY, result.kind)
    }

    @Test
    fun `설날과 추석 당일 및 수능일에는 자습실을 운영하지 않는다`() {
        listOf("설날", "추석", "대학수학능력시험").forEach { title ->
            val date = LocalDate.of(2026, 11, 19)
            val result = AcademicSchedulePolicy.overrideFor(date, listOf(AcademicEvent(date, title = title)))!!

            assertEquals(AcademicScheduleKind.STUDY_ROOM_CLOSED, result.kind)
            assertTrue(result.lessons.isEmpty())
        }
    }

    @Test
    fun `사흘짜리 설날 연휴는 가운데 당일만 운영하지 않는다`() {
        val start = LocalDate.of(2026, 2, 16)
        val event = AcademicEvent(start, start.plusDays(2), "설날 연휴")

        assertEquals(
            AcademicScheduleKind.HOLIDAY_SELF_STUDY,
            AcademicSchedulePolicy.overrideFor(start, listOf(event))?.kind,
        )
        assertEquals(
            AcademicScheduleKind.STUDY_ROOM_CLOSED,
            AcademicSchedulePolicy.overrideFor(start.plusDays(1), listOf(event))?.kind,
        )
        assertEquals(
            AcademicScheduleKind.HOLIDAY_SELF_STUDY,
            AcademicSchedulePolicy.overrideFor(start.plusDays(2), listOf(event))?.kind,
        )
    }

    @Test
    fun `학력평가일은 공식 시험 시간표를 사용한다`() {
        val date = LocalDate.of(2026, 9, 2)

        val result = AcademicSchedulePolicy.overrideFor(date, listOf(AcademicEvent(date, title = "전국연합학력평가")))!!

        assertEquals(AcademicScheduleKind.ACADEMIC_ASSESSMENT, result.kind)
        assertEquals(listOf("국어", "수학", "영어", "한국사", "사회탐구", "과학탐구"), result.lessons.map { it.subject })
        assertEquals(8 * 60 + 40, result.lessons.first().startMinute)
        assertEquals(10 * 60, result.lessons.first().endMinute)
        assertEquals(17 * 60 + 10, result.lessons.last().endMinute)
    }

    @Test
    fun `일반 학사일정은 정규 시간표를 바꾸지 않는다`() {
        val date = LocalDate.of(2026, 8, 18)

        assertNull(AcademicSchedulePolicy.overrideFor(date, listOf(AcademicEvent(date, title = "학생자치회"))))
        assertNull(AcademicSchedulePolicy.overrideFor(date, listOf(AcademicEvent(date, title = "대수능 모의평가"))))
    }
}
