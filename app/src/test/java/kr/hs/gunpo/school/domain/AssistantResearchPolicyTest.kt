package kr.hs.gunpo.school.domain

import org.junit.Assert.*
import org.junit.Test

class AssistantResearchPolicyTest {
    @Test
    fun freshnessAndExplicitSearchRequestsUseResearch() {
        listOf("최신 AI 뉴스 알려줘", "웹 검색해서 학교 소식 찾아줘", "실시간 리서치", "인터넷에서 검색해줘")
            .forEach { assertTrue(it, AssistantResearchPolicy.requestsSearch(it)) }
    }

    @Test
    fun routineSchoolFactsAndFollowUpsDoNotTriggerExternalSearch() {
        listOf("오늘 급식", "오늘 시간표", "지금 몇 교시야?", "오늘 야자", "2차는?", "안드로이드가 뭐야?")
            .forEach { assertFalse(it, AssistantResearchPolicy.requestsSearch(it)) }
    }

    @Test
    fun sourcesExcludeExecutableLocalAndCredentialUrls() {
        listOf("javascript:alert(1)", "file:///sdcard/secret", "https://user:password@example.com/", "https:///missing-host", "not a url")
            .forEach { assertFalse(it, AssistantResearchPolicy.safeSourceUrl(it)) }
        assertTrue(AssistantResearchPolicy.safeSourceUrl("https://firebase.google.com/docs/ai-logic/pricing"))
    }

    @Test
    fun mixedLanguageFreshnessRequestsStillTriggerResearch() {
        listOf("Latest NASA news", "최근 AI 소식", "요즘 교육 정책", "WEB SEARCH NASA")
            .forEach { assertTrue(it, AssistantResearchPolicy.requestsSearch(it)) }
    }

    @Test
    fun latestNewsFiltersRecentArticlesWithoutOverridingHistoricalQueries() {
        assertEquals("week", AssistantResearchPolicy.newsTimeRange("latest NASA news"))
        assertEquals("day", AssistantResearchPolicy.newsTimeRange("오늘 뉴스 검색해줘"))
        assertNull(AssistantResearchPolicy.newsTimeRange("2023 NASA 뉴스 찾아줘"))
        assertNull(AssistantResearchPolicy.newsTimeRange("최근 3년 교육 뉴스"))
        assertNull(AssistantResearchPolicy.newsTimeRange("최신 Firebase 요금"))
    }
}
