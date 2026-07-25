package kr.hs.gunpo.school.domain

import android.util.Log
import kr.hs.gunpo.school.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object CloudGeminiAssistant {
    private const val MAX_REQUESTS_PER_MINUTE = 5
    private val recentRequests = ArrayDeque<Long>()

    fun isConfigured(): Boolean = BuildConfig.GEMINI_API_KEY.isNotBlank()

    suspend fun refine(question: String, factualAnswer: String): String? = withContext(Dispatchers.IO) {
        if (!isConfigured() || !acquireQuota()) return@withContext null
        request(
            """
                너는 군포고등학교 앱의 한국어 학교생활 도우미다.
                아래 확정 정보에 없는 학교 관련 사실은 추측하거나 만들어내지 마라.
                날짜, 시간, 과목, 메뉴를 바꾸지 말고 학생에게 친절한 두세 문장으로 답하라.
                질문: $question
                확정 정보: $factualAnswer
            """.trimIndent(),
        )
    }

    suspend fun answerGeneral(question: String): String? = withContext(Dispatchers.IO) {
        if (!isConfigured() || !acquireQuota()) return@withContext null
        request(
            """
                사용자의 일반적인 질문에 한국어로 정확하고 친절하게 답하라.
                일반 지식 범위에서 충분히 답할 수 있는 질문에는 바로 답하라.
                인터넷이나 데이터베이스에 접속할 수 없다는 포괄적인 안내 문구로 답변을 회피하지 마라.
                실시간 확인이 꼭 필요한 질문만 지식의 기준 시점을 짧게 밝혀라.
                질문: $question
            """.trimIndent(),
        )
    }

    private fun request(prompt: String): String? =
        runCatching {
            val encodedModel = URLEncoder.encode(BuildConfig.GEMINI_CLOUD_MODEL, Charsets.UTF_8.name())
            val encodedKey = URLEncoder.encode(BuildConfig.GEMINI_API_KEY, Charsets.UTF_8.name())
            val connection = URL("https://generativelanguage.googleapis.com/v1beta/models/$encodedModel:generateContent?key=$encodedKey")
                .openConnection() as HttpURLConnection
            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                .put("generationConfig", JSONObject().put("temperature", 0.2).put("maxOutputTokens", 256))
                .toString()
            connection.run {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                try {
                    outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    if (responseCode !in 200..299) return@run null
                    val response = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    JSONObject(response).optJSONArray("candidates")?.optJSONObject(0)
                        ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
                        ?.optString("text")?.trim()?.takeIf { it.isNotBlank() }
                } finally { disconnect() }
            }
        }.getOrNull()

    @Synchronized
    private fun acquireQuota(): Boolean {
        val now = System.currentTimeMillis()
        while (recentRequests.firstOrNull()?.let { now - it >= 60_000 } == true) recentRequests.removeFirst()
        if (recentRequests.size >= MAX_REQUESTS_PER_MINUTE) return false
        recentRequests.addLast(now)
        return true
    }
}

/**
 * Firebase AI Logic routes Gemini traffic without embedding a Gemini API key in the APK.
 * App Check is installed at process startup by [kr.hs.gunpo.school.GunpoSchoolApplication].
 */
object FirebaseCloudAssistant {
    fun isConfigured(): Boolean = BuildConfig.FIREBASE_CONFIGURED

    suspend fun refine(question: String, factualAnswer: String): String? {
        if (!isConfigured()) return null
        val prompt = """
                너는 군포고등학교 앱의 한국어 학교생활 도우미다.
                아래 확정 정보에 없는 학교 관련 사실은 추측하거나 만들어내지 마라.
                날짜, 시간, 과목, 메뉴를 바꾸지 말고 학생에게 친절한 두세 문장으로 답하라.
                질문: $question
                확정 정보: $factualAnswer
            """.trimIndent()
        return generateWithFallback(prompt, "Grounded school answer")
    }

    suspend fun answerGeneral(question: String): String? {
        if (!isConfigured()) return null
        val prompt = """
                사용자의 일반적인 질문에 한국어로 정확하고 친절하게 답하라.
                일반 지식 범위에서 충분히 답할 수 있는 질문에는 바로 답하라.
                인터넷이나 데이터베이스에 접속할 수 없다는 포괄적인 안내 문구로 답변을 회피하지 마라.
                실시간 확인이 꼭 필요한 질문만 지식의 기준 시점을 짧게 밝혀라.
                질문: $question
            """.trimIndent()
        return generateWithFallback(prompt, "General answer")
    }

    private suspend fun generateWithFallback(prompt: String, operation: String): String? {
        val models = listOf(BuildConfig.FIREBASE_AI_MODEL, BuildConfig.FIREBASE_AI_FALLBACK_MODEL).distinct()
        var lastError: Throwable? = null
        for ((modelIndex, modelName) in models.withIndex()) {
            repeat(2) { attempt ->
                val result = runCatching {
                    Firebase.ai(backend = GenerativeBackend.googleAI())
                        .generativeModel(modelName)
                        .generateContent(prompt).text?.trim()?.takeIf { it.isNotBlank() }
                }
                result.getOrNull()?.let { return it }
                lastError = result.exceptionOrNull()
                Log.w("FirebaseCloudAssistant", "$operation failed with $modelName (attempt ${attempt + 1})", lastError)
                if (attempt == 0) delay(1_000L * (modelIndex + 1))
            }
        }
        Log.e("FirebaseCloudAssistant", "$operation failed for all Firebase models", lastError)
        return null
    }
}

object HybridSchoolAssistant {
    suspend fun answer(question: String, factualAnswer: String, isSchoolQuestion: Boolean): String {
        if (!isSchoolQuestion) {
            if (FirebaseCloudAssistant.isConfigured()) {
                return FirebaseCloudAssistant.answerGeneral(question)
                    ?: "Firebase AI가 혼잡하거나 일시적으로 연결되지 않았어요. 잠시 후 다시 시도해 주세요."
            }
            return CloudGeminiAssistant.answerGeneral(question)
                ?: "일반 질문은 클라우드 Gemini 연결 후 답할 수 있어요."
        }
        if (NanoAssistant.isAvailable()) return NanoAssistant.refine(question, factualAnswer)
        if (FirebaseCloudAssistant.isConfigured()) {
            return FirebaseCloudAssistant.refine(question, factualAnswer) ?: factualAnswer
        }
        return CloudGeminiAssistant.refine(question, factualAnswer) ?: factualAnswer
    }
}
