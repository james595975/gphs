package kr.hs.gunpo.school.data

import java.time.LocalDate

data class Lesson(
    val period: Int,
    val subject: String,
    val startMinute: Int,
    val endMinute: Int,
    val room: String = "2학년 1반",
)

data class SchoolDay(
    val dayOfWeek: Int,
    val shortName: String,
    val longName: String,
    val lessons: List<Lesson>,
)

data class Meal(
    val date: LocalDate,
    val type: String = "중식",
    val menu: List<String>,
    val calories: String,
)

data class VacationCourseOption(val id: String, val subject: String, val room: String)

object VacationCourseCatalog {
    private val selfStudy = VacationCourseOption("self_study", "자습", "자습실")
    private val options = mapOf(
        1 to listOf(selfStudy, VacationCourseOption("p1_easy_math", "공통수학 쉬운 개념", "1-1"), VacationCourseOption("p1_korean_ebs", "국어 원기옥 - EBS 수능완성편", "2-2"), VacationCourseOption("p1_geometry", "기상(기하성적 상승비법)", "2-10")),
        2 to listOf(selfStudy, VacationCourseOption("p2_math_four", "공통수학 도전 기출 하루 4문항", "1-1"), VacationCourseOption("p2_science_one", "통합과학 개념 알기1", "1-5"), VacationCourseOption("p2_essay", "논술(원리와 글쓰기 병행)", "1-6"), VacationCourseOption("p2_english_a", "처음부터 시작하는 유형독해 영어A", "1-7"), VacationCourseOption("p2_rooftop", "옥상(옥환&기상)으로 따라와", "2-2"), VacationCourseOption("p2_advanced_math", "고급진 수학 개념 강의", "2-10")),
        3 to listOf(selfStudy, VacationCourseOption("p3_math_four", "공통수학 도전 기출 하루 4문항", "1-1"), VacationCourseOption("p3_science_two", "통합과학 개념 알기2", "1-5"), VacationCourseOption("p3_essay", "논술(원리와 글쓰기 병행)", "1-6"), VacationCourseOption("p3_english_b", "처음부터 시작하는 유형독해 영어B", "1-7"), VacationCourseOption("p3_rooftop", "옥상(옥환&기상)으로 따라와", "2-2"), VacationCourseOption("p3_jinyoung_math", "진영수학(진짜 영재를 위한 수학수업)", "2-10")),
        4 to listOf(selfStudy, VacationCourseOption("p4_literature", "문학 사용설명서: 감이 아니라 근거로 푸는 기출 해부", "1-6"), VacationCourseOption("p4_korean", "이기(이옥환&최기상)는 국어", "2-2"), VacationCourseOption("p4_badminton", "배드민턴", "진행관")),
        5 to listOf(selfStudy, VacationCourseOption("p5_literature", "문학 사용설명서: 감이 아니라 근거로 푸는 기출 해부", "1-6"), VacationCourseOption("p5_grammar", "수능기초문법", "2-9")),
    )

    fun optionsFor(period: Int) = options[period] ?: listOf(selfStudy)
    fun selected(period: Int, id: String?) = optionsFor(period).firstOrNull { it.id == id } ?: selfStudy
}

data class AcademicEvent(
    val start: LocalDate,
    val end: LocalDate = start,
    val title: String,
    val scope: String = "전학년",
    val grades: Set<Int> = emptySet(),
)

data class Notice(
    val category: String,
    val section: String,
    val title: String,
    val dateLabel: String,
    val url: String,
    val isNew: Boolean = false,
)

enum class EighthPeriodMode(val label: String, val subject: String?) {
    EMPTY("비움", null),
    SELF_STUDY("자습", "자습"),
    SUPPLEMENTARY("보충수업", "보충수업"),
}

data class UserSettings(
    val studentName: String = "김동우",
    val studentNumber: String = "20105",
    val grade: Int = 2,
    val classNumber: Int = 1,
    val nightStudyByDay: Map<Int, Int> = (1..5).associateWith { 0 },
    val eighthPeriodByDay: Map<Int, EighthPeriodMode> = (1..5).associateWith { EighthPeriodMode.EMPTY },
    val vacationCourseByPeriod: Map<Int, String> = (1..5).associateWith { "self_study" },
    val liveUpdatesEnabled: Boolean = false,
    val locationMonitoringEnabled: Boolean = false,
) {
    val className: String get() = "${grade}학년 ${classNumber}반"
}
