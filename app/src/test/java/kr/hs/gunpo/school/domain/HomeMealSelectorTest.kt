package kr.hs.gunpo.school.domain

import kr.hs.gunpo.school.data.Meal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class HomeMealSelectorTest {
    private val today = LocalDate.of(2026, 7, 27)
    private val lunch = Meal(today, "중식", listOf("중식"), "")
    private val dinner = Meal(today, "석식", listOf("석식"), "")
    private val tomorrowLunch = Meal(today.plusDays(1), "중식", listOf("내일 중식"), "")
    private val meals = listOf(lunch, dinner, tomorrowLunch)

    @Test
    fun `12시 59분에는 오늘 중식을 선택한다`() {
        val result = HomeMealSelector.select(at(12, 59), meals)
        assertEquals("중식", result.type)
        assertSame(lunch, result.meal)
    }

    @Test
    fun `13시부터 오늘 석식을 선택한다`() {
        val result = HomeMealSelector.select(at(13, 0), meals)
        assertEquals("석식", result.type)
        assertSame(dinner, result.meal)
    }

    @Test
    fun `17시 59분에는 오늘 석식을 선택한다`() {
        val result = HomeMealSelector.select(at(17, 59), meals)
        assertEquals("석식", result.type)
        assertSame(dinner, result.meal)
    }

    @Test
    fun `18시부터 내일 중식을 선택하고 안내한다`() {
        val result = HomeMealSelector.select(at(18, 0), meals)
        assertEquals(today.plusDays(1), result.date)
        assertEquals("중식", result.type)
        assertEquals("내일 중식", result.title)
        assertEquals("내일 제공 예정인 중식입니다.", result.notice)
        assertSame(tomorrowLunch, result.meal)
    }

    private fun at(hour: Int, minute: Int): LocalDateTime = today.atTime(hour, minute)
}