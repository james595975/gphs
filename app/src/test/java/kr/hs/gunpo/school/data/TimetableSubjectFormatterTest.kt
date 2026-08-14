package kr.hs.gunpo.school.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TimetableSubjectFormatterTest {
    @Test
    fun `second grade elective subjects keep their block labels`() {
        val week = LocalDate.of(2026, 8, 17)

        assertEquals("2A", TimetableSubjectFormatter.display("역학", 2, 5, week, 3))
        assertEquals("2B", TimetableSubjectFormatter.display("일본어", 2, 5, week.plusDays(1), 2))
        assertEquals("2C", TimetableSubjectFormatter.display("한문", 2, 5, week.plusDays(4), 3))
        assertEquals("2D", TimetableSubjectFormatter.display("세계영어", 2, 5, week.plusDays(3), 1))
        assertEquals("2E", TimetableSubjectFormatter.display("스포츠2", 2, 5, week.plusDays(4), 5))
        assertEquals("2F", TimetableSubjectFormatter.display("독서B", 2, 5, week.plusDays(2), 1))
    }

    @Test
    fun `class four keeps actual subjects in 2E and 2F blocks`() {
        val week = LocalDate.of(2026, 8, 17)

        assertEquals("인간경제", TimetableSubjectFormatter.display("인간경제", 2, 4, week.plusDays(4), 5))
        assertEquals("스포츠2", TimetableSubjectFormatter.display("스포츠2", 2, 4, week.plusDays(4), 6))
        assertEquals("독서B", TimetableSubjectFormatter.display("독서B", 2, 4, week.plusDays(2), 1))
        assertEquals("2A", TimetableSubjectFormatter.display("역학", 2, 4, week, 3))
    }

    @Test
    fun `ordinary classes and other grades keep their subject names`() {
        val monday = LocalDate.of(2026, 8, 17)

        assertEquals("미적분1", TimetableSubjectFormatter.display("미적분1", 2, 4, monday, 1))
        assertEquals("역학", TimetableSubjectFormatter.display("역학", 3, 4, monday, 3))
    }
}
