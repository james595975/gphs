package kr.hs.gunpo.school.data

import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolDataTest {
    @Test
    fun `class one fallback is not shown to class four`() {
        val date = LocalDate.of(2026, 3, 2)

        assertTrue(SchoolData.lessonsFor(date, UserSettings(grade = 2, classNumber = 4)).isEmpty())
    }
}
