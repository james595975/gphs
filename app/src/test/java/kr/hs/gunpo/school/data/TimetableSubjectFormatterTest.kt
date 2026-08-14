package kr.hs.gunpo.school.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TimetableSubjectFormatterTest {
    @Test
    fun `specified second grade blocks use labels and expanded 2E 2F names`() {
        val monday = LocalDate.of(2026, 8, 17)

        assertEquals("2A", TimetableSubjectFormatter.display("역학", 2, monday, 3))
        assertEquals("2C", TimetableSubjectFormatter.display("한문", 2, monday, 4))
        assertEquals("논리와 사고", TimetableSubjectFormatter.display("논리사고", 2, monday, 6))
        assertEquals("논리와 사고", TimetableSubjectFormatter.display("논리사고", 2, monday.plusDays(1), 1))
        assertEquals("2B", TimetableSubjectFormatter.display("동아시아", 2, monday.plusDays(1), 2))
        assertEquals("인간과 경제활동", TimetableSubjectFormatter.display("인간경제", 2, monday.plusDays(1), 3))
        assertEquals("2D", TimetableSubjectFormatter.display("일본어", 2, monday.plusDays(1), 6))
        assertEquals("2A", TimetableSubjectFormatter.display("역학", 2, monday.plusDays(2), 2))
        assertEquals("논리와 사고", TimetableSubjectFormatter.display("논리사고", 2, monday.plusDays(3), 4))
        assertEquals("2C", TimetableSubjectFormatter.display("한문", 2, monday.plusDays(4), 3))
        assertEquals("인간과 경제활동", TimetableSubjectFormatter.display("인간경제", 2, monday.plusDays(4), 5))
    }

    @Test
    fun `all unspecified periods keep the timetable subject`() {
        val monday = LocalDate.of(2026, 8, 17)

        assertEquals("스포츠2", TimetableSubjectFormatter.display("스포츠2", 2, monday, 1))
        assertEquals("미적분1", TimetableSubjectFormatter.display("미적분1", 2, monday, 2))
        assertEquals("역학", TimetableSubjectFormatter.display("역학", 3, monday, 3))
    }
}
