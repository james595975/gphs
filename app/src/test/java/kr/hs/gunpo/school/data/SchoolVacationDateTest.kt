package kr.hs.gunpo.school.data

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolVacationDateTest {
    @Test
    fun `opening day is not treated as summer vacation`() {
        assertTrue(SchoolData.isVacation(LocalDate.of(2026, 8, 12)))
        assertFalse(SchoolData.isVacation(LocalDate.of(2026, 8, 13)))
    }
}
