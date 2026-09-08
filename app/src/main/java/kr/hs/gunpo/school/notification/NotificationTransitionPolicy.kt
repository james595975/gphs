package kr.hs.gunpo.school.notification

import kr.hs.gunpo.school.data.Lesson
import kr.hs.gunpo.school.domain.SchoolMoment
import kr.hs.gunpo.school.domain.SchoolTimeline
import java.time.LocalDateTime

internal object NotificationTransitionPolicy {
    fun alertKey(now: LocalDateTime, moment: SchoolMoment): String? = when (moment) {
        is SchoolMoment.InClass -> "${now.toLocalDate()}:period:${moment.lesson.period}:${moment.lesson.startMinute}"
        is SchoolMoment.LunchBreak -> "${now.toLocalDate()}:lunch"
        else -> null
    }

    fun nextBoundary(now: LocalDateTime, lessons: List<Lesson>): LocalDateTime {
        val minute = now.hour * 60 + now.minute
        val boundaries = lessons.flatMap { listOf(it.startMinute, it.endMinute) }.toMutableList()
        if (lessons.any { it.endMinute <= SchoolTimeline.LUNCH_START_MINUTE }) {
            boundaries += SchoolTimeline.LUNCH_START_MINUTE
            boundaries += SchoolTimeline.LUNCH_END_MINUTE
        }
        val next = boundaries.filter { it > minute }.minOrNull()
        return if (next != null) now.toLocalDate().atStartOfDay().plusMinutes(next.toLong())
        else now.toLocalDate().plusDays(1).atTime(7, 30)
    }
}
