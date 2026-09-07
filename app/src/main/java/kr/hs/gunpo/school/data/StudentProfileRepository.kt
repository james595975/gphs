package kr.hs.gunpo.school.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.hs.gunpo.school.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class SavedStudentProfile(
    val name: String,
    val studentNumber: String,
    val maskedPhoneNumber: String,
)

class StudentProfileRepository {
    suspend fun load(firebaseIdToken: String): Result<SavedStudentProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val response = request("GET", firebaseIdToken)
            val profile = requireNotNull(response.optJSONObject("profile")) { "서버 응답이 올바르지 않습니다." }
            SavedStudentProfile(
                name = profile.getString("name"),
                studentNumber = profile.getString("studentNumber"),
                maskedPhoneNumber = profile.getString("phoneNumberMasked"),
            )
        }
    }

    suspend fun save(
        firebaseIdToken: String,
        name: String,
        studentNumber: String,
    ): Result<SavedStudentProfile> = withContext(Dispatchers.IO) {
        runCatching {
            require(BuildConfig.CLOUDFLARE_API_BASE_URL.isNotBlank()) {
                "개인정보 저장 서버가 설정되지 않았습니다."
            }
            val body = JSONObject()
                .put("name", name.trim())
                .put("studentNumber", studentNumber.trim())
                .put("consent", true)
                .put("consentVersion", CONSENT_VERSION)
                .toString()
            val response = request("PUT", firebaseIdToken, body)
            val profile = requireNotNull(response.optJSONObject("profile")) { "서버 응답이 올바르지 않습니다." }
            SavedStudentProfile(
                name = profile.getString("name"),
                studentNumber = profile.getString("studentNumber"),
                maskedPhoneNumber = profile.getString("phoneNumberMasked"),
            )
        }
    }

    private fun request(method: String, firebaseIdToken: String, body: String? = null): JSONObject {
        require(BuildConfig.CLOUDFLARE_API_BASE_URL.isNotBlank()) { "개인정보 저장 서버가 설정되지 않았습니다." }
        val connection = (URL("${BuildConfig.CLOUDFLARE_API_BASE_URL}/v1/profile")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = body != null
            setRequestProperty("Authorization", "Bearer $firebaseIdToken")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        return try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val responseCode = connection.responseCode
            val responseText = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val response = runCatching { JSONObject(responseText) }.getOrElse { JSONObject() }
            if (responseCode !in 200..299) {
                error(response.optString("error").takeIf(String::isNotBlank) ?: "학생 정보를 불러오지 못했습니다.")
            }
            response
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val CONSENT_VERSION = "2026-09-04"
    }
}
