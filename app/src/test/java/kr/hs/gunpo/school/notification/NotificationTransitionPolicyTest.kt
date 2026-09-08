package kr.hs.gunpo.school.notification

import kr.hs.gunpo.school.data.Lesson
import kr.hs.gunpo.school.domain.SchoolTimeline
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class NotificationTransitionPolicyTest {
    private val day = LocalDate.of(2026, 9, 8)
    private val lessons = listOf(Lesson(4, "국어", 680, 730), Lesson(5, "영어", 790, 840), Lesson(6, "수학", 850, 900))
    private fun key(hour: Int, minute: Int): String? {
        val now = day.atTime(hour, minute)
        return NotificationTransitionPolicy.alertKey(now, SchoolTimeline.moment(now, lessons))
    }

    @Test fun `같은 교시 갱신은 같은 알림키를 사용한다`() {
        assertEquals(key(11, 20), key(11, 40))
        assertNotEquals(key(11, 20), key(13, 10))
        assertNull(key(14, 5))
        assertNull(key(15, 1))
    }

    @Test fun `점심은 수업과 별도 알림이고 같은 날 중복되지 않는다`() {
        assertNotNull(key(12, 10))
        assertEquals(key(12, 10), key(13, 9))
        assertNotEquals(key(12, 10), key(13, 10))
    }

    @Test fun `목록 정렬과 무관하게 가장 가까운 수업 경계를 예약한다`() {
        assertEquals(day.atTime(12, 10), NotificationTransitionPolicy.nextBoundary(day.atTime(12, 9), lessons.reversed()))
        assertEquals(day.atTime(13, 10), NotificationTransitionPolicy.nextBoundary(day.atTime(12, 10), lessons.reversed()))
        assertEquals(day.atTime(14, 10), NotificationTransitionPolicy.nextBoundary(day.atTime(14, 0), lessons.reversed()))
    }

    @Test fun `일과 종료 후와 수업 없는 날에는 다음날 재조회한다`() {
        assertEquals(day.plusDays(1).atTime(7, 30), NotificationTransitionPolicy.nextBoundary(day.atTime(15, 0), lessons))
        assertEquals(day.plusDays(1).atTime(7, 30), NotificationTransitionPolicy.nextBoundary(day.atTime(12, 0), emptyList()))
    }
}
