package kr.hs.gunpo.school.domain

import kr.hs.gunpo.school.data.Lesson
import java.time.LocalDateTime

sealed interface SchoolMoment {
    data class InClass(val lesson: Lesson, val next: Lesson?) : SchoolMoment
    data class BetweenClasses(val previous: Lesson, val next: Lesson) : SchoolMoment
    data class BeforeSchool(val next: Lesson) : SchoolMoment
    data class Finished(val last: Lesson) : SchoolMoment
    data object NoSchool : SchoolMoment
}

object SchoolTimeline {
    fun moment(at: LocalDateTime, lessons: List<Lesson>): SchoolMoment {
        if (lessons.isEmpty()) return SchoolMoment.NoSchool
        val minute = at.hour * 60 + at.minute
        lessons.forEachIndexed { index, lesson ->
            if (minute in lesson.startMinute until lesson.endMinute) return SchoolMoment.InClass(lesson, lessons.getOrNull(index + 1))
        }
        if (minute < lessons.first().startMinute) return SchoolMoment.BeforeSchool(lessons.first())
        lessons.zipWithNext().firstOrNull { minute in it.first.endMinute until it.second.startMinute }?.let {
            return SchoolMoment.BetweenClasses(it.first, it.second)
        }
        return SchoolMoment.Finished(lessons.last())
    }

    fun clock(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)
}
