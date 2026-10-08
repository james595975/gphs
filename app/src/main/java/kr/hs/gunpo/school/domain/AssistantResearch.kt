package kr.hs.gunpo.school.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import com.google.firebase.auth.FirebaseAuth
import kr.hs.gunpo.school.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ResearchSource(val title: String, val url: String, val excerpt: String)
data class ResearchResult(val sources: List<ResearchSource>, val checkedAt: Instant)

class ResearchUnavailableException(message: String) : Exception(message)

object AssistantResearchPolicy {
    fun requestsSearch(question: String): Boolean {
        val normalized = question.lowercase().replace(" ", "")
        return listOf("웹검색", "인터넷검색", "검색해", "검색해서", "찾아봐", "찾아줘", "실시간", "리서치", "최신", "최근", "요즘", "오늘뉴스", "현재뉴스", "latest", "websearch", "research")
            .any(normalized::contains)
    }

    fun safeSourceUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)

    fun isNewsQuery(question: String): Boolean = question.contains("뉴스") || question.contains("news", ignoreCase = true)

    fun newsTimeRange(question: String): String? {
        if (!isNewsQuery(question)) return null
        // Respect an explicit date or historical period in the user's request.
        if (Regex("(?:19|20)\\d{2}|\\d+\\s*(?:년|개월|달|월|주|일|years?|months?|weeks?|days?)", RegexOption.IGNORE_CASE).containsMatchIn(question)) return null
        val normalized = question.lowercase()
        if (listOf("오늘", "today").any(normalized::contains)) return "day"
        return if (listOf("최신", "최근", "요즘", "실시간", "latest", "recent").any(normalized::contains)) "week" else null
    }

}

/** Keyless access is free and rate limited; it never upgrades to a paid search request. */
object AssistantWebResearch {
    suspend fun summarize(question: String, result: ResearchResult): String {
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw ResearchUnavailableException("무료 AI 요약은 로그인 후 사용할 수 있어요.")
        val token = suspendCancellableCoroutine<String> { continuation ->
            user.getIdToken(false).addOnCompleteListener { task ->
                if (!continuation.isActive) return@addOnCompleteListener
                val value = if (task.isSuccessful) task.result?.token else null
                if (value != null) continuation.resume(value)
                else continuation.resumeWithException(ResearchUnavailableException("로그인 정보를 확인하지 못했어요. 다시 로그인해 주세요."))
            }
        }
        return withContext(Dispatchers.IO) {
            if (BuildConfig.CLOUDFLARE_API_BASE_URL.isBlank()) throw ResearchUnavailableException("무료 AI 요약 서버가 설정되지 않았어요.")
            val connection = URL("${BuildConfig.CLOUDFLARE_API_BASE_URL}/v1/research/summary").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 10_000
                connection.readTimeout = 35_000
                connection.instanceFollowRedirects = false
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.setRequestProperty("Authorization", "Bearer $token")
                val sources = JSONArray()
                result.sources.forEach { source ->
                    sources.put(JSONObject().put("title", source.title.take(100)).put("url", source.url).put("excerpt", source.excerpt.take(700)))
                }
                val payload = JSONObject().put("question", question.trim()).put("checkedAt", result.checkedAt.toString()).put("sources", sources)
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val bytes = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.use { it.readNBytes(64 * 1024) }
                val response = bytes?.let { JSONObject(it.toString(Charsets.UTF_8)) }
                if (status !in 200..299) throw ResearchUnavailableException(response?.optString("error")?.takeIf { it.isNotBlank() }
                    ?: "무료 AI 요약에 연결할 수 없어요.")
                response?.optString("text")?.takeIf { it.isNotBlank() }
                    ?: throw ResearchUnavailableException("무료 AI 요약 결과가 비어 있어요.")
            } finally {
                connection.disconnect()
            }
        }
    }

    suspend fun search(question: String): ResearchResult = withContext(Dispatchers.IO) {
        val query = question.trim()
        if (query.isEmpty() || query.length > 500) {
            throw ResearchUnavailableException("웹 검색 질문은 500자 이내로 입력해 주세요.")
        }
        val connection = URL("https://api.tavily.com/search").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 25_000
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("X-Tavily-Access-Mode", "keyless")
            connection.setRequestProperty("X-Client-Source", "gphs-android")
            // Only the current question is sent. Student settings and chat history are excluded.
            val payload = JSONObject()
                .put("query", query)
                .put("search_depth", "basic")
                .put("max_results", 5)
                .put("include_answer", false)
                .put("include_raw_content", false)
                .put("topic", if (AssistantResearchPolicy.isNewsQuery(query)) "news" else "general")
                .put("include_published_date", true)
            AssistantResearchPolicy.newsTimeRange(query)?.let { payload.put("time_range", it) }
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status == 429 || status == 432 || status == 433) {
                throw ResearchUnavailableException("무료 웹 검색 한도에 도달했어요. 잠시 후 다시 시도해 주세요. 유료 검색으로 자동 전환하지 않습니다.")
            }
            if (status !in 200..299) throw ResearchUnavailableException("지금 웹 검색에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.")
            val bytes = connection.inputStream.use { it.readNBytes(512 * 1024 + 1) }
            if (bytes.size > 512 * 1024) throw ResearchUnavailableException("검색 응답이 너무 커서 표시할 수 없어요.")
            val response = JSONObject(bytes.toString(Charsets.UTF_8))
            if (response.has("error")) throw ResearchUnavailableException("무료 웹 검색을 일시적으로 사용할 수 없어요. 잠시 후 다시 시도해 주세요.")
            val results = response.optJSONArray("results")
            val sources = buildList {
                for (index in 0 until (results?.length() ?: 0)) {
                    val entry = results?.optJSONObject(index) ?: continue
                    val url = entry.optString("url")
                    if (!AssistantResearchPolicy.safeSourceUrl(url)) continue
                    val content = entry.optString("content")
                    if (content.isBlank()) continue
                    val date = entry.optString("published_date").takeIf { it.isNotBlank() && it != "null" }
                    val excerpt = (date?.let { "게시일: $it\n" }.orEmpty() + content).take(2400)
                    add(ResearchSource(entry.optString("title").ifBlank { URI(url).host }.take(180), url, excerpt))
                }
            }.distinctBy { it.url }.take(5)
            if (sources.isEmpty()) throw ResearchUnavailableException("확인할 수 있는 검색 결과가 없어요. 질문을 더 구체적으로 입력해 주세요.")
            ResearchResult(sources, Instant.now())
        } finally {
            connection.disconnect()
        }
    }
}
