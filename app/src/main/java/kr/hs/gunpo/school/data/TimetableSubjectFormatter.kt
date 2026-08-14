package kr.hs.gunpo.school.data

import java.time.LocalDate

object TimetableSubjectFormatter {
    private val secondGradeSemesterTwo = LocalDate.of(2026, 8, 13)..LocalDate.of(2027, 2, 28)
    private val electiveBlocks = mapOf(
        (1 to 3) to "2A", (1 to 4) to "2C", (1 to 6) to "논리와 사고",
        (2 to 1) to "논리와 사고", (2 to 2) to "2B", (2 to 3) to "인간과 경제활동",
        (2 to 5) to "2C", (2 to 6) to "2D",
        (3 to 2) to "2A", (3 to 3) to "2B",
        (4 to 1) to "2D", (4 to 4) to "논리와 사고", (4 to 5) to "2A", (4 to 6) to "2B",
        (5 to 3) to "2C", (5 to 4) to "2D", (5 to 5) to "인간과 경제활동",
    )

    fun display(subject: String, grade: Int, date: LocalDate, period: Int): String {
        if (grade != 2 || date !in secondGradeSemesterTwo) return subject
        return electiveBlocks[date.dayOfWeek.value to period] ?: subject
    }
}
