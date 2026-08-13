package kr.hs.gunpo.school.data

data class StudentNumber(
    val value: String,
    val grade: Int,
    val classNumber: Int,
    val seatNumber: Int,
)

fun parseStudentNumber(rawValue: String): StudentNumber? {
    val value = rawValue.trim()
    if (value.length != 5 || value.any { !it.isDigit() }) return null

    val grade = value.substring(0, 1).toInt()
    val classNumber = value.substring(1, 3).toInt()
    val seatNumber = value.substring(3, 5).toInt()
    if (grade !in 1..3 || classNumber !in 1..20 || seatNumber !in 1..99) return null

    return StudentNumber(value, grade, classNumber, seatNumber)
}
