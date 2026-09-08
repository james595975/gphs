package kr.hs.gunpo.school.domain

import kr.hs.gunpo.school.data.AcademicEvent
import java.time.LocalDate

object HomeAcademicEventPolicy {
    private val importantKeywords = listOf(
        "개학", "입학", "졸업", "시험", "평가", "고사", "수능",
        "체육", "축제", "행사", "상담", "설명회", "방학",
        "공휴일", "추석", "설날", "현장체험",
    )

    fun isImportant(event: AcademicEvent): Boolean {
        if (event.title.contains("토요휴업일")) return false
        return event.scope == "공휴일" || importantKeywords.any(event.title::contains)
    }

    fun upcoming(events: List<AcademicEvent>, from: LocalDate, limit: Int = 3): List<AcademicEvent> =
        events.asSequence()
            .filter { !it.end.isBefore(from) }
            .filter(::isImportant)
            .sortedBy { it.start }
            .take(limit)
            .toList()
}
