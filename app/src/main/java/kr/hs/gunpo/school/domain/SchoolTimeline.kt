package kr.hs.gunpo.school.domain

import kr.hs.gunpo.school.data.Lesson
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

sealed interface SchoolMoment {
    data class InClass(val lesson: Lesson, val next: Lesson?) : SchoolMoment
    data class BetweenClasses(val previous: Lesson, val next: Lesson) : SchoolMoment
    data class LunchBreak(val next: Lesson?) : SchoolMoment
    data class BeforeSchool(val next: Lesson) : SchoolMoment
    data class Finished(val last: Lesson) : SchoolMoment
    data object NoSchool : SchoolMoment
}

object SchoolTimeline {
    const val LUNCH_START_MINUTE = 12 * 60 + 10
    const val LUNCH_END_MINUTE = 13 * 60 + 10
    private const val DEFAULT_SCHOOL_END_MINUTE = 16 * 60

    fun moment(at: LocalDateTime, lessons: List<Lesson>): SchoolMoment {
        if (lessons.isEmpty()) return SchoolMoment.NoSchool
        val minute = at.hour * 60 + at.minute
        val ordered = lessons.sortedBy(Lesson::startMinute)
        ordered.forEachIndexed { index, lesson ->
            if (minute in lesson.startMinute until lesson.endMinute) return SchoolMoment.InClass(lesson, ordered.getOrNull(index + 1))
        }
        if (minute in LUNCH_START_MINUTE until LUNCH_END_MINUTE && ordered.any { it.endMinute <= LUNCH_START_MINUTE }) {
            return SchoolMoment.LunchBreak(ordered.firstOrNull { it.startMinute > minute })
        }
        if (minute < ordered.first().startMinute) return SchoolMoment.BeforeSchool(ordered.first())
        ordered.zipWithNext().firstOrNull { minute in it.first.endMinute until it.second.startMinute }?.let {
            return SchoolMoment.BetweenClasses(it.first, it.second)
        }
        return SchoolMoment.Finished(ordered.last())
    }

    fun clock(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

    /**
     * 시간표 화면을 처음 열거나 날짜가 바뀔 때 기본으로 보여 줄 날짜다.
     * 금요일 일과가 끝난 뒤와 주말에는 다가오는 월요일을 보여 준다.
     */
    fun defaultTimetableDate(at: LocalDateTime, todayLessons: List<Lesson>): LocalDate {
        val today = at.toLocalDate()
        return when (today.dayOfWeek) {
            DayOfWeek.FRIDAY -> {
                val schoolEndMinute = todayLessons.maxOfOrNull(Lesson::endMinute)
                    ?: DEFAULT_SCHOOL_END_MINUTE
                val currentMinute = at.hour * 60 + at.minute
                if (currentMinute >= schoolEndMinute) today.plusDays(3) else today
            }
            DayOfWeek.SATURDAY -> today.plusDays(2)
            DayOfWeek.SUNDAY -> today.plusDays(1)
            else -> today
        }
    }
}
