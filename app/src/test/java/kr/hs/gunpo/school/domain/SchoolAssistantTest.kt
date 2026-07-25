package kr.hs.gunpo.school.domain

import kr.hs.gunpo.school.data.SchoolData
import kr.hs.gunpo.school.data.UserSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class SchoolAssistantTest {
    @Test
    fun generalQuestionIsNotGroundedAsSchoolData() {
        assertFalse(SchoolAssistant.isSchoolQuestion("안드로이드가 뭐야?"))
        assertTrue(SchoolAssistant.isSchoolQuestion("오늘 석식 뭐야?"))
    }

    @Test
    fun dinnerQuestionReturnsOnlySwiftDinnerData() {
        val now = LocalDateTime.of(2026, 7, 24, 12, 0)
        val answer = SchoolAssistant.answer(
            question = "오늘 석식 뭐야?",
            now = now,
            settings = UserSettings(),
            syncedMeals = SchoolData.meals.filter { it.date == now.toLocalDate() },
        )

        assertTrue(answer.contains("석식:"))
        assertTrue(answer.contains("돼지국밥"))
        assertFalse(answer.contains("짜장밥"))
    }
}
