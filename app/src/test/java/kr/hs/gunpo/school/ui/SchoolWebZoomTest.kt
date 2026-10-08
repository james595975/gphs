package kr.hs.gunpo.school.ui

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolWebZoomTest {
    @Test
    fun replacesDuplicateZoomRestrictionsAndPreservesAttachmentLink() {
        val url = "https://www.gunpo.hs.kr/main.php?act=view"
        val html = """<html><head>
            <meta name="viewport" content="width=device-width, maximum-scale=1, user-scalable=no">
            <meta name="viewport" content="minimum-scale=1">
            </head><body><a href="main.php?mode=download&amp;sid=25681">학교 첨부파일.jpg</a>
            <img src="main.php?mode=download&amp;sid=25681"></body></html>"""
        val document = Jsoup.parse(SchoolWebZoom.prepareHtml(html, url), url)
        assertEquals(1, document.select("meta[name=viewport]").size)
        val viewport = document.selectFirst("meta[name=viewport]")!!.attr("content")
        assertTrue(viewport.contains("maximum-scale=5"))
        assertTrue(viewport.contains("user-scalable=yes"))
        assertEquals("https://www.gunpo.hs.kr/main.php?mode=download&sid=25681", document.selectFirst("a")!!.absUrl("href"))
        assertEquals("학교 첨부파일.jpg", document.selectFirst("a")!!.text())
        assertEquals(1, document.select("img").size)
    }
}
