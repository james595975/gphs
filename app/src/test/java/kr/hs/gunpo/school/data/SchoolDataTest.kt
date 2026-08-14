package kr.hs.gunpo.school.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolDataTest {
    @Test
    fun `class one fallback is not shown to class four`() {
        val date = LocalDate.of(2026, 3, 2)

        assertTrue(SchoolData.lessonsFor(date, UserSettings(grade = 2, classNumber = 4)).isEmpty())
    }

    @Test
    fun `화요일 보충 과목은 8교시에 교사 정보와 함께 표시한다`() {
        val settings = UserSettings(
            eighthPeriodByDay = (1..5).associateWith { day ->
                if (day in setOf(2, 5)) EighthPeriodMode.SUPPLEMENTARY else EighthPeriodMode.EMPTY
            },
            supplementaryCourseByGroup = mapOf(
                SupplementaryCourseCatalog.TUESDAY_FRIDAY to "bisanggu",
            ),
        )

        val lesson = SchoolData.additionalLessonsFor(LocalDate.of(2026, 8, 18), settings).single()

        assertEquals(8, lesson.period)
        assertEquals("비.상.구", lesson.subject)
        assertEquals("비문학 점수 기상T와 구출하기 · 교사 최기상", lesson.room)
    }

    @Test
    fun `목요일 논술은 9교시와 10교시에 표시한다`() {
        val settings = UserSettings(
            supplementaryCourseByGroup = mapOf(
                SupplementaryCourseCatalog.THURSDAY_LATE to "essay_argument",
            ),
        )

        val lessons = SchoolData.additionalLessonsFor(LocalDate.of(2026, 8, 20), settings)

        assertEquals(listOf(9, 10), lessons.map { it.period })
        assertTrue(lessons.all { it.subject == "논술" })
        assertTrue(lessons.all { it.room == "논증 및 비판 글쓰기 · 교사 이복락" })
        assertEquals(18 * 60 + 10, lessons[0].startMinute)
        assertEquals(20 * 60, lessons[0].endMinute)
        assertEquals(20 * 60 + 10, lessons[1].startMinute)
        assertEquals(22 * 60, lessons[1].endMinute)
    }

    @Test
    fun `등록한 보충 과목은 지정된 요일 외에는 표시하지 않는다`() {
        val settings = UserSettings(
            supplementaryCourseByGroup = mapOf(
                SupplementaryCourseCatalog.THURSDAY_LATE to "essay_argument",
            ),
        )

        assertTrue(SchoolData.additionalLessonsFor(LocalDate.of(2026, 8, 21), settings).isEmpty())
    }
}
