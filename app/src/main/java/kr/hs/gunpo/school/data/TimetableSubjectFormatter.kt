package kr.hs.gunpo.school.data

import java.time.LocalDate

object TimetableSubjectFormatter {
    private val secondGradeSemesterTwo = LocalDate.of(2026, 8, 13)..LocalDate.of(2027, 2, 28)
    private val electiveBlocks = mapOf(
        (1 to 3) to "2A", (3 to 2) to "2A", (4 to 5) to "2A",
        (2 to 2) to "2B", (3 to 3) to "2B", (4 to 6) to "2B",
        (1 to 4) to "2C", (2 to 5) to "2C", (5 to 3) to "2C",
        (2 to 6) to "2D", (4 to 1) to "2D", (5 to 4) to "2D",
        (5 to 5) to "2E", (5 to 6) to "2E",
        (1 to 2) to "2F", (2 to 7) to "2F", (3 to 1) to "2F",
    )

    fun display(subject: String, grade: Int, date: LocalDate, period: Int): String {
        if (grade != 2 || date !in secondGradeSemesterTwo) return subject
        return electiveBlocks[date.dayOfWeek.value to period] ?: subject
    }
}
