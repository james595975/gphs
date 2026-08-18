package kr.hs.gunpo.school.notification

import kr.hs.gunpo.school.data.Lesson
import kr.hs.gunpo.school.data.NeisState
import kr.hs.gunpo.school.data.SchoolData
import kr.hs.gunpo.school.data.UserSettings
import kr.hs.gunpo.school.domain.AcademicSchedulePolicy
import java.time.LocalDate
import java.time.YearMonth

internal data class NotificationSchedule(
    val lessons: List<Lesson>,
    val isAvailable: Boolean,
)

internal object NotificationScheduleResolver {
    fun resolve(
        date: LocalDate,
        settings: UserSettings,
        neisState: NeisState,
    ): NotificationSchedule {
        val events = (neisState.events.ifEmpty { SchoolData.events })
            .filter { it.grades.isEmpty() || settings.grade in it.grades }
        AcademicSchedulePolicy.overrideFor(date, events)?.let {
            return NotificationSchedule(it.lessons, isAvailable = true)
        }

        if (SchoolData.isVacation(date)) {
            return NotificationSchedule(SchoolData.lessonsFor(date, settings), isAvailable = true)
        }

        val localFallback = SchoolData.lessonsFor(date, settings)
        if (neisState.grade != settings.grade || neisState.classNumber != settings.classNumber) {
            return NotificationSchedule(localFallback, isAvailable = localFallback.isNotEmpty())
        }

        neisState.timetableByDate[date]?.let { remote ->
            val additional = SchoolData.additionalLessonsFor(date, settings)
                .filter { extra -> remote.none { it.period == extra.period } }
            return NotificationSchedule(remote + additional, isAvailable = true)
        }

        if (neisState.errorMessage == null && YearMonth.from(date) in neisState.loadedMonths) {
            return NotificationSchedule(emptyList(), isAvailable = true)
        }

        return NotificationSchedule(localFallback, isAvailable = localFallback.isNotEmpty())
    }
}
