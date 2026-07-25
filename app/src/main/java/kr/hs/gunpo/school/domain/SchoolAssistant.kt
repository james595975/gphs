package kr.hs.gunpo.school.domain

import kr.hs.gunpo.school.data.SchoolData
import kr.hs.gunpo.school.data.UserSettings
import kr.hs.gunpo.school.data.Lesson
import kr.hs.gunpo.school.data.Meal
import java.time.LocalDateTime

object SchoolAssistant {
    private val schoolKeywords = listOf(
        "학교", "군포고", "시간표", "수업", "과목", "교시", "급식", "중식", "점심",
        "석식", "저녁", "밥", "메뉴", "야자", "야간자습", "학사일정", "학교일정",
        "개학", "방학", "시험", "행사",
    )

    fun isSchoolQuestion(question: String): Boolean {
        val normalized = question.replace(" ", "").lowercase()
        return schoolKeywords.any(normalized::contains)
    }

    fun answer(
        question: String,
        now: LocalDateTime,
        settings: UserSettings,
        syncedLessons: List<Lesson>? = null,
        syncedMeals: List<Meal>? = null,
    ): String {
        val query = question.replace(" ", "")
        val lessons = syncedLessons ?: SchoolData.lessonsFor(now.toLocalDate(), settings)
        return when {
            "급식" in query || "중식" in query || "점심" in query || "석식" in query || "저녁" in query || "밥" in query || "메뉴" in query -> {
                val meals = syncedMeals ?: SchoolData.meals.filter { it.date == now.toLocalDate() }
                val requestedType = when {
                    "석식" in query || "저녁" in query -> "석식"
                    "중식" in query || "점심" in query -> "중식"
                    else -> null
                }
                val selectedMeals = requestedType?.let { type -> meals.filter { it.type == type } } ?: meals
                if (selectedMeals.isEmpty()) "오늘 등록된 ${requestedType ?: "급식"} 정보가 없어요."
                else selectedMeals.joinToString("\n\n") { "${it.type}: ${it.menu.joinToString(" · ")}\n${it.calories}" }
            }
            "지금" in query || "현재" in query || "몇교시" in query -> when (val moment = SchoolTimeline.moment(now, lessons)) {
                is SchoolMoment.InClass -> "지금은 ${moment.lesson.period}교시 ${moment.lesson.subject} 수업 중이에요. ${SchoolTimeline.clock(moment.lesson.endMinute)}에 끝나요."
                is SchoolMoment.BetweenClasses -> "지금은 쉬는 시간이에요. ${SchoolTimeline.clock(moment.next.startMinute)}에 ${moment.next.period}교시 ${moment.next.subject}이 시작해요."
                is SchoolMoment.BeforeSchool -> "아직 수업 전이에요. ${SchoolTimeline.clock(moment.next.startMinute)}에 ${moment.next.subject} 수업이 시작해요."
                is SchoolMoment.Finished -> "오늘 수업은 모두 끝났어요."
                SchoolMoment.NoSchool -> "오늘은 등록된 수업이 없어요."
            }
            "야자" in query || "야간자습" in query -> {
                val value = settings.nightStudyByDay[now.dayOfWeek.value] ?: 0
                when (value) { 1 -> "오늘 야간자습은 1차까지 설정되어 있어요."; 2 -> "오늘 야간자습은 2차까지 설정되어 있어요."; else -> "오늘은 야간자습을 하지 않는 것으로 설정되어 있어요." }
            }
            "8교시" in query -> {
                val lesson = lessons.firstOrNull { it.period == 8 }
                lesson?.let { "오늘 8교시는 ${it.subject}이고, ${SchoolTimeline.clock(it.startMinute)}부터 ${SchoolTimeline.clock(it.endMinute)}까지예요." }
                    ?: "오늘은 8교시가 없어요. 설정에서 요일별 8교시를 바꿀 수 있어요."
            }
            "시간표" in query || "수업" in query || "과목" in query -> {
                if (lessons.isEmpty()) "오늘은 등록된 수업이 없어요."
                else lessons.joinToString("\n") { "${it.period}교시 ${it.subject}  ${SchoolTimeline.clock(it.startMinute)}–${SchoolTimeline.clock(it.endMinute)}${if (it.room.isNotBlank()) " · ${it.room}" else ""}" }
            }
            else -> "현재 앱에 등록된 학교 데이터에서는 답을 찾지 못했어요."
        }
    }
}
