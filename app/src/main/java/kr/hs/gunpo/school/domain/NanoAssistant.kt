package kr.hs.gunpo.school.domain

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation

object NanoAssistant {
    private val model by lazy { Generation.getClient() }

    suspend fun isAvailable(): Boolean = runCatching {
        model.checkStatus() == FeatureStatus.AVAILABLE
    }.getOrDefault(false)

    suspend fun refine(question: String, factualAnswer: String): String {
        if (!isAvailable()) return factualAnswer
        val prompt = """
            너는 군포고등학교 앱의 한국어 학교생활 도우미다.
            아래 '확정 정보'에 없는 사실은 절대로 추가하거나 추측하지 마라.
            날짜, 시간, 과목, 메뉴를 바꾸지 말고 학생에게 친절한 두세 문장으로 답하라.
            질문: $question
            확정 정보: $factualAnswer
        """.trimIndent()
        return runCatching {
            model.generateContent(prompt).candidates.firstOrNull()?.text?.trim().takeUnless { it.isNullOrBlank() }
                ?: factualAnswer
        }.getOrDefault(factualAnswer)
    }
}
