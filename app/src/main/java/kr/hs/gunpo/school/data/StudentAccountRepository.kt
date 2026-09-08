package kr.hs.gunpo.school.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.hs.gunpo.school.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class SavedStudentAccount(val loginId: String, val email: String)

class StudentAccountRepository {
    suspend fun save(firebaseIdToken: String, loginId: String): Result<SavedStudentAccount> =
        authenticatedRequest(
            "PUT",
            firebaseIdToken,
            JSONObject()
                .put("loginId", loginId)
                .put("consent", true)
                .put("consentVersion", ACCOUNT_CONSENT_VERSION),
        ).map(::accountFrom)

    suspend fun load(firebaseIdToken: String): Result<SavedStudentAccount> =
        authenticatedRequest("GET", firebaseIdToken).map(::accountFrom)

    suspend fun resolveLogin(loginId: String, password: String): Result<String> = publicRequest(
        "/v1/auth/resolve-login",
        JSONObject().put("loginId", loginId).put("password", password),
    ).map { it.getString("email") }

    suspend fun requestPasswordReset(loginId: String): Result<String> = publicRequest(
        "/v1/auth/password-reset",
        JSONObject().put("loginId", loginId),
    ).map { it.optString("message", "등록된 계정이면 비밀번호 재설정 메일을 보냈습니다.") }

    suspend fun requestLoginId(email: String): Result<String> = publicRequest(
        "/v1/auth/find-login-id",
        JSONObject().put("email", email),
    ).map { it.optString("message", "등록된 이메일이면 아이디 확인 링크를 보냈습니다.") }

    suspend fun startSocial(provider: String, firebaseIdToken: String?, codeChallenge: String): Result<String> = withContext(Dispatchers.IO) {
        request("/v1/auth/$provider/start", "POST", JSONObject().put("codeChallenge", codeChallenge), firebaseIdToken)
            .map { it.getString("authorizeUrl") }
    }

    suspend fun socialLinked(provider: String, token: String): Result<Boolean> = withContext(Dispatchers.IO) {
        request("/v1/auth/$provider/status", "GET", null, token).map { it.getBoolean("linked") }
    }

    suspend fun unlinkSocial(provider: String, token: String): Result<Boolean> = withContext(Dispatchers.IO) {
        request("/v1/auth/$provider/status", "DELETE", null, token).map { !it.getBoolean("linked") }
    }

    suspend fun exchangeSocial(provider: String, code: String, codeVerifier: String): Result<String> = publicRequest(
        "/v1/auth/$provider/exchange",
        JSONObject().put("code", code).put("codeVerifier", codeVerifier),
    ).map { it.getString("customToken") }

    private fun accountFrom(response: JSONObject): SavedStudentAccount {
        val account = requireNotNull(response.optJSONObject("account")) { "서버 응답이 올바르지 않습니다." }
        return SavedStudentAccount(account.getString("loginId"), account.getString("email"))
    }

    private suspend fun authenticatedRequest(
        method: String,
        firebaseIdToken: String,
        body: JSONObject? = null,
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        request("/v1/account", method, body, firebaseIdToken)
    }

    private suspend fun publicRequest(path: String, body: JSONObject): Result<JSONObject> =
        withContext(Dispatchers.IO) { request(path, "POST", body, null) }

    private fun request(
        path: String,
        method: String,
        body: JSONObject?,
        firebaseIdToken: String?,
    ): Result<JSONObject> = runCatching {
        require(BuildConfig.CLOUDFLARE_API_BASE_URL.isNotBlank()) { "계정 서버가 설정되지 않았습니다." }
        val connection = (URL("${BuildConfig.CLOUDFLARE_API_BASE_URL}$path")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = body != null
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            firebaseIdToken?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        try {
            body?.let { payload -> connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) } }
            val responseCode = connection.responseCode
            val responseText = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val response = runCatching { JSONObject(responseText) }.getOrElse { JSONObject() }
            if (responseCode !in 200..299) {
                error(response.optString("error").takeIf(String::isNotBlank) ?: "계정 요청을 처리하지 못했습니다.")
            }
            response
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val ACCOUNT_CONSENT_VERSION = "2026-09-07"
    }
}
