package kr.hs.gunpo.school.domain

import kr.hs.gunpo.school.data.AcademicEvent
import kr.hs.gunpo.school.data.Lesson
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class AcademicScheduleKind {
    HOLIDAY_SELF_STUDY,
    STUDY_ROOM_CLOSED,
    ACADEMIC_ASSESSMENT,
}

data class AcademicScheduleOverride(
    val kind: AcademicScheduleKind,
    val eventTitle: String,
    val lessons: List<Lesson>,
)

object AcademicSchedulePolicy {
    private val publicHolidayKeywords = listOf(
        "공휴일", "대체공휴일", "신정", "1월1일", "삼일절", "3·1절", "3.1절",
        "제헌절", "광복절", "개천절", "한글날", "부처님오신날", "석가탄신일",
        "노동절", "근로자의날", "어린이날", "현충일", "성탄절", "기독탄신일",
        "설날", "추석", "대체휴일", "임시공휴일", "선거일", "대통령선거", "지방선거", "국회의원선거",
    )

    private val regularStarts = listOf(8 * 60 + 20, 9 * 60 + 20, 10 * 60 + 20, 11 * 60 + 20, 13 * 60 + 10, 14 * 60 + 10, 15 * 60 + 10)

    fun overrideFor(date: LocalDate, events: List<AcademicEvent>): AcademicScheduleOverride? {
        val eventsOnDate = events.filter { date in it.start..it.end }

        eventsOnDate.firstOrNull { isStudyRoomClosed(date, it) }?.let { event ->
            return AcademicScheduleOverride(AcademicScheduleKind.STUDY_ROOM_CLOSED, event.title, emptyList())
        }
        eventsOnDate.firstOrNull(::isAcademicAssessment)?.let { event ->
            return AcademicScheduleOverride(AcademicScheduleKind.ACADEMIC_ASSESSMENT, event.title, assessmentLessons())
        }
        eventsOnDate.firstOrNull(::isPublicHoliday)?.let { event ->
            return AcademicScheduleOverride(AcademicScheduleKind.HOLIDAY_SELF_STUDY, event.title, selfStudyLessons())
        }
        return null
    }

    private fun isStudyRoomClosed(date: LocalDate, event: AcademicEvent): Boolean {
        val title = normalized(event.title)
        if (("수능" in title || "대학수학능력시험" in title) && "모의" !in title) return true
        val holidayName = when {
            "설날" in title -> "설날"
            "추석" in title -> "추석"
            else -> return false
        }
        if ("대체" in title) return false
        if (title == holidayName || title.startsWith("$holidayName(") || "${holidayName}당일" in title) return true
        if (event.start == event.end) return false
        val middleDate = event.start.plusDays(ChronoUnit.DAYS.between(event.start, event.end) / 2)
        return date == middleDate
    }

    private fun isAcademicAssessment(event: AcademicEvent): Boolean {
        val title = normalized(event.title)
        return "학력평가" in title || "전국연합" in title
    }

    private fun isPublicHoliday(event: AcademicEvent): Boolean {
        if (event.scope == "공휴일") return true
        val title = normalized(event.title)
        return publicHolidayKeywords.any(title::contains)
    }

    private fun selfStudyLessons(): List<Lesson> = regularStarts.mapIndexed { index, start ->
        Lesson(index + 1, "자습", start, start + 50, "자습실")
    }

    private fun assessmentLessons() = listOf(
        Lesson(1, "국어", 8 * 60 + 40, 10 * 60, "전국연합학력평가"),
        Lesson(2, "수학", 10 * 60 + 30, 12 * 60 + 10, "전국연합학력평가"),
        Lesson(3, "영어", 13 * 60 + 10, 14 * 60 + 20, "전국연합학력평가"),
        Lesson(4, "한국사", 14 * 60 + 50, 15 * 60 + 20, "전국연합학력평가"),
        Lesson(5, "사회탐구", 15 * 60 + 35, 16 * 60 + 15, "전국연합학력평가"),
        Lesson(6, "과학탐구", 16 * 60 + 30, 17 * 60 + 10, "전국연합학력평가"),
    )

    private fun normalized(value: String) = value.replace(" ", "").lowercase()
}
