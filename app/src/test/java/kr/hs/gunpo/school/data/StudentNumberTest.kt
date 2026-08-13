package kr.hs.gunpo.school.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudentNumberTest {
    @Test
    fun `20415 is parsed as second grade class four seat fifteen`() {
        val result = parseStudentNumber("20415")

        assertEquals(2, result?.grade)
        assertEquals(4, result?.classNumber)
        assertEquals(15, result?.seatNumber)
    }

    @Test
    fun `invalid student numbers are rejected`() {
        assertNull(parseStudentNumber("2015"))
        assertNull(parseStudentNumber("22115"))
        assertNull(parseStudentNumber("20400"))
    }
}
