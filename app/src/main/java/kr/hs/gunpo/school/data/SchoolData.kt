package kr.hs.gunpo.school.data

import java.time.DayOfWeek
import java.time.LocalDate

object SchoolData {
    private val starts = listOf(8 * 60 + 20, 9 * 60 + 20, 10 * 60 + 20, 11 * 60 + 20, 13 * 60 + 10, 14 * 60 + 10, 15 * 60 + 10)
    private val ends = starts.map { it + 50 }

    private fun lessons(subjects: List<String>) = subjects.mapIndexed { index, subject ->
        Lesson(index + 1, subject, starts[index], ends[index])
    }

    val days = listOf(
        SchoolDay(1, "월", "월요일", lessons(listOf("운동건강", "2F", "2A", "2C", "문학", "대수", "자율"))),
        SchoolDay(2, "화", "화요일", lessons(listOf("대수", "2B", "영어", "문학", "2C", "2D", "2F"))),
        SchoolDay(3, "수", "수요일", lessons(listOf("2F", "2A", "2B", "자율", "자율", "자율", "자율"))),
        SchoolDay(4, "목", "목요일", lessons(listOf("2D", "진로", "영어", "대수", "2A", "2B", "운동건강"))),
        SchoolDay(5, "금", "금요일", lessons(listOf("영어", "문학", "2C", "2D", "2E", "2E", "자율"))),
    )

    fun lessonsFor(date: LocalDate, settings: UserSettings): List<Lesson> {
        if (isVacation(date)) {
            if (date.dayOfWeek.value !in 1..5) return emptyList()
            val ranges = listOf(500 to 570, 580 to 650, 660 to 730, 790 to 860, 870 to 940)
            return ranges.mapIndexed { index, range ->
                val period = index + 1
                val option = VacationCourseCatalog.selected(period, settings.vacationCourseByPeriod[period])
                Lesson(period, option.subject, range.first, range.second, option.room)
            }
        }
        // 내장 정규 시간표는 2학년 1반 자료다. 다른 반에 이를 대신 표시하지 않는다.
        if (settings.grade != 2 || settings.classNumber != 1) return emptyList()
        val day = days.firstOrNull { it.dayOfWeek == date.dayOfWeek.value } ?: return emptyList()
        return day.lessons + additionalLessonsFor(date, settings)
    }

    fun additionalLessonsFor(date: LocalDate, settings: UserSettings): List<Lesson> {
        val day = date.dayOfWeek.value
        if (day !in 1..5) return emptyList()
        val additional = mutableListOf<Lesson>()
        val eighthMode = settings.eighthPeriodByDay[day] ?: EighthPeriodMode.EMPTY
        if (eighthMode.subject != null) {
            val group = SupplementaryCourseCatalog.groups.firstOrNull { it.usesEighthPeriod && day in it.days }
            val course = group?.let { SupplementaryCourseCatalog.selected(it.id, settings) }
            val subject = if (eighthMode == EighthPeriodMode.SUPPLEMENTARY) course?.subject ?: eighthMode.subject else eighthMode.subject
            val room = if (eighthMode == EighthPeriodMode.SUPPLEMENTARY) course?.detail ?: settings.className else settings.className
            additional += Lesson(8, subject, 16 * 60 + 20, 17 * 60 + 10, room)
        }

        val lateGroup = SupplementaryCourseCatalog.group(SupplementaryCourseCatalog.THURSDAY_LATE)
        val lateCourse = lateGroup?.takeIf { day in it.days }?.let { SupplementaryCourseCatalog.selected(it.id, settings) }
        if (lateCourse != null) {
            additional += Lesson(9, lateCourse.subject, 18 * 60 + 10, 20 * 60, lateCourse.detail)
            additional += Lesson(10, lateCourse.subject, 20 * 60 + 10, 22 * 60, lateCourse.detail)
        }
        return additional
    }

    fun isVacation(date: LocalDate) = !date.isBefore(LocalDate.of(2026, 7, 17)) && date.isBefore(LocalDate.of(2026, 8, 13))

    val meals = listOf(
        meal(2026, 7, 13, "발아현미밥 · 오이냉국 · 고구마줄기볶음 · 새우튀김 · 돼지갈비구이 · 배추겉절이 · 레몬에이드", "1,068.9 kcal"),
        meal(2026, 7, 14, "귀리밥 · 비빔쌀국수 · 짬뽕국 · 꽈리고추달걀장조림 · 교자만두탕수 · 배추겉절이 · 포도", "1,442.2 kcal"),
        meal(2026, 7, 15, "근대된장국 · 수육&쌈장 · 유기농매실절임 · 삼색나물비빔밥 · 상추파채무침 · 계란후라이 · 배추김치 · 오미자주스", "1,150.5 kcal"),
        meal(2026, 7, 20, "클로렐라밥 · 경상도식쇠고기국 · 닭살하이라이스조림 · 오믈렛 · 살구미니파이 · 배추김치 · 수박화채", "여름방학 위탁급식"),
        meal(2026, 7, 20, "클로렐라밥 · 돈목살김치찌개 · 소떡소떡 · 어묵꽈리볶음 · 찹쌀꽈배기 · 배추김치 · 자몽에이드", "여름방학 위탁급식", type = "석식"),
        meal(2026, 7, 21, "차조밥 · 무채된장국 · 돈육바베큐소스조림 · 어묵고로케/칠리소스 · 마카로니콘치즈구이 · 배추김치 · 바나나우유", "여름방학 위탁급식"),
        meal(2026, 7, 21, "보조밥 · 닭칼국수 · 매콤돈육불고기 · 새우튀김 · 오이부추생채 · 배추김치 · 메론푸딩", "여름방학 위탁급식", type = "석식"),
        meal(2026, 7, 22, "치킨비빔밥(백미밥+불닭조림+스크램블) · 모듬어묵탕 · 갈비만두 · 배추김치 · 망고", "여름방학 위탁급식"),
        meal(2026, 7, 22, "백미밥 · 미트볼부대찌개 · 순대야채볶음 · 고구마야채튀김 · 파래김/참치마요장 · 배추김치 · 레몬에이드", "여름방학 위탁급식", type = "석식"),
        meal(2026, 7, 23, "흑미밥 · 도토리묵냉채 · 춘천닭갈비 · 감자채전 · 오이야채피클 · 깍두기 · 파인애플스틱", "여름방학 위탁급식"),
        meal(2026, 7, 23, "흑미밥 · 맑은콩나물국 · 치즈불닭 · 클래식버거 · 배추김치 · 쥬시쿨에이드", "여름방학 위탁급식", type = "석식"),
        meal(2026, 7, 24, "짜장밥 · 꿔바로우탕수 · 꽃맛살샐러드 · 배추김치 · 녹여먹는요구르트", "여름방학 위탁급식"),
        meal(2026, 7, 24, "찰현미밥 · 돼지국밥 · 달걀말이 · 깻잎튀김 · 백오이소박이 · 배추김치 · 대파크림팝콘", "여름방학 위탁급식", type = "석식"),
        meal(2026, 7, 27, "백미밥 · 맑은미역국 · 돈육불고기 · 비엔나야채볶음 · 야채절이 · 깍두기 · 계절과일", "여름방학 위탁급식"),
        meal(2026, 7, 27, "백미밥 · 대파육개장 · 코다리강정 · 김치왕만두 · 야채절이 · 깍두기 · 계절과일", "여름방학 위탁급식", type = "석식"),
        meal(2026, 7, 28, "백미밥 · 초계탕 · 돈육사태단호박찜 · 감자채전 · 야채초절이 · 배추김치 · 컵푸딩", "여름방학 위탁급식"),
        meal(2026, 7, 28, "너비아니메밀비빔면 · 달걀장국 · 닭볶음탕 · 배추김치 · 컵푸딩", "여름방학 위탁급식", type = "석식"),
        meal(2026, 7, 29, "푸팟퐁커리덮밥 · 치킨까스/소스 · 수제피클 · 깍두기 · 바게트", "여름방학 위탁급식"),
        meal(2026, 7, 29, "백미밥 · 돈등뼈감자탕 · 새우까스 · 달걀찜 · 중화식가지나물 · 깍두기 · 요구르트펀치", "여름방학 위탁급식", type = "석식"),
        meal(2026, 7, 30, "차조밥 · 목살백김치찌개 · 달걀말이 · 도토리묵냉채 · 핫도그 · 배추김치 · 계절과일", "여름방학 위탁급식"),
        meal(2026, 7, 30, "차조밥 · 맑은콩나물국 · 마라상궈 · 간장치킨강정 · 도토리묵오이냉채 · 배추겉절이 · 컵쥬스", "여름방학 위탁급식", type = "석식"),
        meal(2026, 7, 31, "후랑크라따뚜이오므라이스 · 참치김치찌개 · 스프링롤/칠리소스 · 배추김치 · 바나나우유", "여름방학 위탁급식"),
        meal(2026, 7, 31, "백미밥 · 쇠고기떡국 · 햇감자닭볶음탕 · 부추야채전 · 오이야채절이 · 배추김치 · 핫도그", "여름방학 위탁급식", type = "석식"),
        meal(2026, 8, 3, "혼합잡곡밥 · 순두부계란국 · 파채돈육간장불고기 · 부추해물전 · 깻잎부추무침 · 백김치 · 수박화채", "여름방학 위탁급식"),
        meal(2026, 8, 3, "흑미밥 · 쇠고기무우국 · 달걀말이 · 구운고로케 · 새송이초무침 · 배추김치 · 회오리감자", "여름방학 위탁급식", type = "석식"),
        meal(2026, 8, 4, "찰현미밥 · 꼬치어묵탕 · 닭살바베큐소스조림 · 크림떡볶이 · 스위치온샐러드 · 배추김치 · 황도", "여름방학 위탁급식"),
        meal(2026, 8, 4, "냉멸치국수(보조밥+소면+멸치육수) · 치킨까스 · 양배추사과샐러드 · 깍두기 · 수박주스", "여름방학 위탁급식", type = "석식"),
        meal(2026, 8, 5, "제육덮밥 · 두부야채국 · 돈육강정 · 카브리제샐러드 · 단무지무침 · 깍두기 · 메론", "여름방학 위탁급식"),
        meal(2026, 8, 5, "백미밥 · 참치김치찌개 · 돈육고추튀김 · 로제누들떡볶이 · 야채샐러드 · 배추김치 · 치즈볼", "여름방학 위탁급식", type = "석식"),
        meal(2026, 8, 6, "흑미밥 · 쇠고기무우국 · 오리불고기 · 야채계란찜 · 생크림과일샐러드 · 토마토김치 · 거꾸로요구르트", "여름방학 위탁급식"),
        meal(2026, 8, 6, "혼합잡곡밥 · 쇠고기미역국 · 돈육달걀장조림 · 어묵소떡 · 꽃맛살샐러드 · 깍두기", "여름방학 위탁급식", type = "석식"),
        meal(2026, 8, 7, "차조밥 · 경상도식국밥 · 돈육바짝불고기 · 떠먹는고구마피자 · 오이피망깍둑초무침 · 깍두기 · 꿀토마토", "여름방학 위탁급식"),
        meal(2026, 8, 7, "차조밥 · 백짬뽕탕 · 닭강정 · 골뱅이초무침 · 도토리묵오이냉채 · 배추김치 · 딸기바나나라떼", "여름방학 위탁급식", type = "석식"),
    )

    val events = listOf(
        event(3, 3, "개학식", "2~3학년", 2, 3), event(3, 24, "전국연합학력평가"),
        event(4, 27, "1학기 1차 지필평가"), event(5, 8, "체육한마당"),
        event(5, 28, "개교기념일"), event(6, 29, "1학기 2차 지필평가"),
        event(7, 16, "여름방학식"), event(7, 17, "제헌절", "국경일"),
        event(8, 13, "2학기 개학식"), event(9, 2, "전국연합학력평가", "1~2학년", 1, 2),
        event(9, 30, "2학기 1차 지필평가"), event(10, 9, "한글날", "공휴일"),
        event(10, 20, "전국연합학력평가"), event(10, 21, "현장체험학습", "1~2학년", 1, 2),
        event(12, 14, "2학기 2차 지필평가", "1~2학년", 1, 2), event(12, 31, "꿈성장 발표회", "오전"),
    )

    val notices = listOf(
        Notice("가정", "가정통신문", "군포의왕 고교학점제 기반 1:1 맞춤형 대입진학 컨설팅 안내", "2026.07.13", "https://www.gunpo.hs.kr/main.php?act=view&master=bbs&master_sid=65&menugrp=060300&sid=12367", true),
        Notice("학사", "가정통신문", "1학기 학기말 추정 분할 점수 안내", "2026.07.10", "https://www.gunpo.hs.kr/main.php?act=view&master=bbs&master_sid=65&menugrp=060300&sid=12366"),
        Notice("안전", "가정통신문", "하계방학 물놀이 안전수칙 및 대처요령", "2026.07.10", "https://www.gunpo.hs.kr/main.php?act=view&master=bbs&master_sid=65&menugrp=060300&sid=12365"),
        Notice("통학", "가정통신문", "2학기 통학버스 안내", "2026.07.10", "https://www.gunpo.hs.kr/main.php?act=view&master=bbs&master_sid=65&menugrp=060300&sid=12364"),
        Notice("공지", "공지사항", "2027학년도 대학수학능력시험 시행세부계획 공고문 및 보도자료 알림", "2026.07.03", "https://www.gunpo.hs.kr/main.php?act=view&master=bbs&master_sid=63&menugrp=060100&sid=12121"),
        Notice("공지", "공지사항", "교육시설안전 인증서 게시", "2026.06.30", "https://www.gunpo.hs.kr/main.php?act=view&master=bbs&master_sid=63&menugrp=060100&sid=12120"),
        Notice("공지", "공지사항", "2026 군포·의왕다움 공유학교 프로그램 (3기) 수강 신청 안내", "2026.06.25", "https://www.gunpo.hs.kr/main.php?act=view&master=bbs&master_sid=63&menugrp=060100&sid=12119"),
        Notice("공지", "공지사항", "2026 경기온라인학교 [제2차 실시간 화상 강좌] 운영 안내", "2026.06.25", "https://www.gunpo.hs.kr/main.php?act=view&master=bbs&master_sid=63&menugrp=060100&sid=12118"),
    )

    fun mealFor(date: LocalDate, type: String = "중식") = meals.firstOrNull { it.date == date && it.type == type }
    fun upcomingEvents(from: LocalDate, grade: Int) = events.filter { !it.end.isBefore(from) && (it.grades.isEmpty() || grade in it.grades) }.sortedBy { it.start }

    private fun meal(year: Int, month: Int, day: Int, menu: String, calories: String, type: String = "중식") =
        Meal(LocalDate.of(year, month, day), type, menu.split(" · "), calories)

    private fun event(month: Int, day: Int, title: String, scope: String = "전학년", vararg grades: Int) =
        AcademicEvent(LocalDate.of(2026, month, day), title = title, scope = scope, grades = grades.toSet())
}
