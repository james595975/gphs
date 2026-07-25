package kr.hs.gunpo.school.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SchoolNoticeParserTest {
    @Test
    fun `게시판 행을 날짜순 공지로 변환한다`() {
        val html = """
            <table>
              <tr><td>공지</td><td><a href="main.php?act=view&amp;sid=1" title="오래된 고정 공지">오래된 고정 공지</a></td><td>관리자</td><td>2026-04-13</td></tr>
              <tr><td>1659</td><td><a href="main.php?act=view&amp;sid=2">2학기 임시시간표</a></td><td>교무부</td><td>2026-07-24</td></tr>
            </table>
        """.trimIndent()

        val notices = SchoolNoticeParser.parse(
            html = html,
            baseUrl = "https://www.gunpo.hs.kr/main.php?menugrp=060100",
            section = "공지사항",
            category = "공지",
            today = LocalDate.of(2026, 7, 25),
        )

        assertEquals(listOf("2학기 임시시간표", "오래된 고정 공지"), notices.map { it.title })
        assertEquals("2026.07.24", notices.first().dateLabel)
        assertEquals("https://www.gunpo.hs.kr/main.php?act=view&sid=2", notices.first().url)
        assertTrue(notices.first().isNew)
        assertFalse(notices.last().isNew)
    }
}
