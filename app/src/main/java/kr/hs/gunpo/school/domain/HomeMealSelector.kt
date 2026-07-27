package kr.hs.gunpo.school.domain

import kr.hs.gunpo.school.data.Meal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class HomeMealPresentation(
    val date: LocalDate,
    val type: String,
    val meal: Meal?,
    val title: String,
    val notice: String?,
)

object HomeMealSelector {
    private val dinnerStart = LocalTime.of(13, 0)
    private val tomorrowLunchStart = LocalTime.of(18, 0)

    fun select(now: LocalDateTime, meals: List<Meal>): HomeMealPresentation {
        val selection = when {
            !now.toLocalTime().isBefore(tomorrowLunchStart) -> Selection(
                date = now.toLocalDate().plusDays(1),
                type = "중식",
                title = "내일 중식",
                notice = "내일 제공 예정인 중식입니다.",
            )
            !now.toLocalTime().isBefore(dinnerStart) -> Selection(
                date = now.toLocalDate(),
                type = "석식",
                title = "오늘의 급식",
                notice = null,
            )
            else -> Selection(
                date = now.toLocalDate(),
                type = "중식",
                title = "오늘의 급식",
                notice = null,
            )
        }

        return HomeMealPresentation(
            date = selection.date,
            type = selection.type,
            meal = meals.firstOrNull { it.date == selection.date && it.type == selection.type },
            title = selection.title,
            notice = selection.notice,
        )
    }

    private data class Selection(
        val date: LocalDate,
        val type: String,
        val title: String,
        val notice: String?,
    )
}