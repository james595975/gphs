package kr.hs.gunpo.school.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.activity.compose.LocalActivity
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import kr.hs.gunpo.school.MainViewModel
import kr.hs.gunpo.school.data.UserSettings
import kr.hs.gunpo.school.data.StudentAccountRepository
import kr.hs.gunpo.school.ui.theme.SchoolBlue
import kr.hs.gunpo.school.ui.theme.SchoolGreen
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

private enum class LoginMode { LOGIN, FIND_ID, RESET_PASSWORD }

private const val GITHUB_OAUTH_REDIRECT_URI = "https://gphs-beta.firebaseapp.com/__/auth/handler"

@Composable
fun LoginScreen(
    viewModel: MainViewModel,
    incomingEmailLink: String?,
    onRegister: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val auth = remember { FirebaseAuth.getInstance() }
    var mode by remember { mutableStateOf(LoginMode.LOGIN) }
    var loginId by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val pendingResult = auth.pendingAuthResult ?: return@LaunchedEffect
        busy = true
        pendingResult.addOnCompleteListener { task ->
            busy = false
            if (!task.isSuccessful) {
                message = "소셜 로그인 복귀 처리를 완료하지 못했습니다. 다시 시도해 주세요."
            }
        }
    }

    LaunchedEffect(incomingEmailLink) {
        val link = incomingEmailLink ?: return@LaunchedEffect
        if (!auth.isSignInWithEmailLink(link)) return@LaunchedEffect
        val pendingEmail = context.getSharedPreferences("auth_recovery", Context.MODE_PRIVATE)
            .getString("pending_find_id_email", null)
        if (pendingEmail == null) {
            message = "아이디 찾기를 요청한 기기에서 링크를 열어 주세요."
            return@LaunchedEffect
        }
        busy = true
        auth.signInWithEmailLink(pendingEmail, link).addOnCompleteListener { task ->
            busy = false
            if (task.isSuccessful) {
                context.getSharedPreferences("auth_recovery", Context.MODE_PRIVATE).edit().clear().apply()
                message = "이메일 확인이 완료되었습니다. 계정 정보를 불러옵니다."
            } else {
                message = "이메일 확인 링크가 만료되었거나 이미 사용되었습니다."
            }
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("GPHS", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = SchoolBlue)
            Text(
                when (mode) {
                    LoginMode.LOGIN -> "군포고 학교생활 계정 로그인"
                    LoginMode.FIND_ID -> "아이디 찾기"
                    LoginMode.RESET_PASSWORD -> "비밀번호 재설정"
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(24.dp))
            if (mode == LoginMode.FIND_ID) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim().take(254); message = null },
                    label = { Text("가입 시 인증한 이메일") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = loginId,
                    onValueChange = { loginId = it.lowercase().filter { character -> character.isLetterOrDigit() || character == '_' }.take(20); message = null },
                    label = { Text("아이디") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (mode == LoginMode.LOGIN) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it.take(128); message = null },
                        label = { Text("비밀번호") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            message?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = if (it.contains("완료") || it.contains("보냈")) SchoolGreen else MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    busy = true
                    message = null
                    when (mode) {
                        LoginMode.LOGIN -> viewModel.resolveLogin(loginId, password, onSuccess = { resolvedEmail ->
                            auth.signInWithEmailAndPassword(resolvedEmail, password).addOnCompleteListener { task ->
                                busy = false
                                if (!task.isSuccessful) message = "아이디 또는 비밀번호가 올바르지 않습니다."
                            }
                        }, onError = { busy = false; message = it })
                        LoginMode.FIND_ID -> {
                            context.getSharedPreferences("auth_recovery", Context.MODE_PRIVATE).edit()
                                .putString("pending_find_id_email", email.lowercase()).apply()
                            viewModel.requestLoginId(email) { busy = false; message = it }
                        }
                        LoginMode.RESET_PASSWORD -> viewModel.requestPasswordReset(loginId) { busy = false; message = it }
                    }
                },
                enabled = !busy && when (mode) {
                    LoginMode.LOGIN -> loginId.length >= 4 && password.length >= 8
                    LoginMode.FIND_ID -> email.contains('@')
                    LoginMode.RESET_PASSWORD -> loginId.length >= 4
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(if (busy) "처리 중…" else when (mode) {
                    LoginMode.LOGIN -> "로그인"
                    LoginMode.FIND_ID -> "이메일 확인 링크 보내기"
                    LoginMode.RESET_PASSWORD -> "재설정 메일 보내기"
                })
            }
            if (mode == LoginMode.LOGIN) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { mode = LoginMode.FIND_ID; message = null }) { Text("아이디 찾기") }
                    TextButton(onClick = { mode = LoginMode.RESET_PASSWORD; message = null }) { Text("비밀번호 재설정") }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text("소셜 로그인", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                SocialProviderButtons(
                    activity = activity,
                    currentUser = null,
                    onBusy = { busy = it },
                    onMessage = { message = it },
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onRegister, modifier = Modifier.fillMaxWidth()) { Text("새 계정 만들기") }
                Text(
                    "소셜로 처음 로그인한 경우에도 학생정보와 SMS 인증을 1회 진행합니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                TextButton(onClick = { mode = LoginMode.LOGIN; message = null }) { Text("로그인으로 돌아가기") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
        }
    }
}

@Composable
fun AccountSetupScreen(
    settings: UserSettings,
    viewModel: MainViewModel,
    onComplete: () -> Unit,
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val user = auth.currentUser
    var hasPasswordProvider by remember(user?.uid) {
        mutableStateOf(user?.providerData?.any { it.providerId == EmailAuthProvider.PROVIDER_ID } == true)
    }
    var loginId by remember { mutableStateOf(settings.accountId) }
    var email by remember { mutableStateOf(user?.email.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var accountConsented by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("로그인 계정 만들기", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("전화번호 인증은 완료되었습니다. 재로그인에 사용할 ID와 이메일을 등록해 주세요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(loginId, { loginId = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }.take(20) }, label = { Text("아이디 (4~20자)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(email, { email = it.trim().take(254); message = null }, label = { Text("이메일") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
            if (!hasPasswordProvider) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(password, { password = it.take(128) }, label = { Text("비밀번호 (8자 이상)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(confirmPassword, { confirmPassword = it.take(128) }, label = { Text("비밀번호 확인") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.Top) {
                Checkbox(checked = accountConsented, onCheckedChange = { accountConsented = it })
                Text(
                    "로그인 ID·인증 이메일·소셜 연결 정보를 계정 제공 목적으로 수집하고 암호화하여 Cloudflare D1(APAC)에 저장하는 것에 동의합니다. 비밀번호는 Firebase Authentication에만 저장됩니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)) }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val current = auth.currentUser
                    if (current == null) { message = "로그인이 만료되었습니다."; return@Button }
                    busy = true
                    message = null
                    if (!hasPasswordProvider) {
                        current.linkWithCredential(EmailAuthProvider.getCredential(email.lowercase(), password)).addOnCompleteListener { linkTask ->
                            if (!linkTask.isSuccessful) {
                                busy = false
                                message = accountAuthError(linkTask.exception)
                            } else {
                                hasPasswordProvider = true
                                auth.setLanguageCode("ko")
                                auth.currentUser?.sendEmailVerification()?.addOnCompleteListener { mailTask ->
                                    busy = false
                                    if (mailTask.isSuccessful) {
                                        message = "인증 메일을 보냈습니다. 링크를 누른 뒤 아래 버튼을 눌러 주세요."
                                    } else message = "인증 메일을 보내지 못했습니다. 아래 인증메일 보내기를 눌러 다시 시도해 주세요."
                                }
                            }
                        }
                    } else {
                        current.reload().addOnCompleteListener { reloadTask ->
                            if (!reloadTask.isSuccessful) {
                                busy = false
                                message = "인증 상태를 확인하지 못했습니다. 네트워크와 로그인 상태를 확인한 뒤 다시 시도해 주세요."
                                return@addOnCompleteListener
                            }
                            val refreshed = auth.currentUser
                            if (refreshed?.isEmailVerified != true || !refreshed.email.equals(email, ignoreCase = true)) {
                                busy = false
                                message = "아래 인증메일 보내기를 누르고, 입력한 이메일로 받은 링크를 연 뒤 다시 확인해 주세요."
                                return@addOnCompleteListener
                            }
                            refreshed.getIdToken(true).addOnCompleteListener { tokenTask ->
                                val token = tokenTask.result?.token
                                if (token == null) {
                                    busy = false
                                    message = "로그인 정보를 갱신하지 못했습니다."
                                } else viewModel.saveAccount(token, refreshed.uid, loginId, onSuccess = {
                                    busy = false
                                    onComplete()
                                }, onError = { busy = false; message = it })
                            }
                        }
                    }
                },
                enabled = accountConsented && !busy && loginId.length >= 4 &&
                    email.contains('@') && (hasPasswordProvider || (password.length >= 8 && password == confirmPassword)),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (busy) "처리 중…" else if (hasPasswordProvider) "이메일 인증 완료 확인" else "계정 생성 및 인증메일 전송") }
            if (hasPasswordProvider) {
                OutlinedButton(
                    onClick = {
                        val current = auth.currentUser
                        if (current == null) { message = "로그인이 만료되었습니다."; return@OutlinedButton }
                        busy = true
                        message = null
                        auth.setLanguageCode("ko")
                        val destination = email.lowercase()
                        val mailTask = if (current.email.equals(destination, ignoreCase = true)) {
                            current.sendEmailVerification()
                        } else {
                            current.verifyBeforeUpdateEmail(destination)
                        }
                        mailTask.addOnCompleteListener { result ->
                            busy = false
                            message = if (result.isSuccessful) {
                                "$destination 주소로 인증메일을 보냈습니다. 스팸함도 확인해 주세요. 링크를 연 뒤 인증 완료 확인을 눌러 주세요."
                            } else when ((result.exception as? com.google.firebase.auth.FirebaseAuthException)?.errorCode) {
                                "ERROR_TOO_MANY_REQUESTS" -> "메일 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."
                                "ERROR_REQUIRES_RECENT_LOGIN" -> "이메일 변경을 위해 다시 로그인한 후 시도해 주세요."
                                "ERROR_OPERATION_NOT_ALLOWED" -> "이메일 인증이 서버에서 비활성화되어 있습니다. 관리자에게 문의해 주세요."
                                else -> "인증메일을 보내지 못했습니다. 이메일 주소와 네트워크를 확인해 주세요."
                            }
                        }
                    },
                    enabled = !busy && accountConsented && email.contains('@'),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("인증메일 보내기 / 재발송") }
                Text("이메일을 잘못 입력했다면 위 주소를 수정하고 인증메일을 다시 보내 주세요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun AccountSettingsScreen(
    settings: UserSettings,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val user = auth.currentUser
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val providers = user?.providerData?.map { it.providerId }?.toSet().orEmpty()
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("뒤로") }
                Text("계정 및 소셜 연동", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Surface(shape = MaterialTheme.shapes.large, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("아이디  ${settings.accountId}", fontWeight = FontWeight.SemiBold)
                    Text(settings.accountEmail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("휴대전화 인증 완료", color = SchoolGreen, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("소셜 계정 연결", fontWeight = FontWeight.Bold)
            Text("연결하면 다음 로그인부터 해당 계정을 사용할 수 있습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            SocialProviderButtons(
                activity = LocalActivity.current,
                currentUser = user,
                linkedProviders = providers,
                onBusy = { busy = it },
                onMessage = { message = it },
            )
            message?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = {
                    auth.signOut()
                    onSignOut()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("로그아웃") }
        }
    }
}

@Composable
private fun SocialProviderButtons(
    activity: Activity?,
    currentUser: FirebaseUser?,
    linkedProviders: Set<String> = emptySet(),
    onBusy: (Boolean) -> Unit,
    onMessage: (String?) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val auth = remember { FirebaseAuth.getInstance() }
    val googleLinked = GoogleAuthProvider.PROVIDER_ID in linkedProviders
    val githubLinked = "github.com" in linkedProviders

    fun oauth(providerId: String, label: String) {
        if (activity == null) { onMessage("인증 화면을 열 수 없습니다."); return }
        onBusy(true)
        onMessage(null)
        val provider = OAuthProvider.newBuilder(providerId).apply {
            if (providerId == "github.com") scopes = listOf("user:email")
        }.build()
        val task = if (currentUser == null) auth.startActivityForSignInWithProvider(activity, provider)
        else currentUser.startActivityForLinkWithProvider(activity, provider)
        task.addOnCompleteListener {
            onBusy(false)
            if (!it.isSuccessful) {
                onMessage(
                    if (providerId == "github.com") {
                        "GitHub 인증을 완료하지 못했습니다. GitHub Redirect URI와 Firebase Client ID/Secret을 확인해 주세요: $GITHUB_OAUTH_REDIRECT_URI"
                    } else {
                        "$label 인증을 완료하지 못했습니다. Firebase 공급자 설정을 확인해 주세요."
                    },
                )
            }
            else onMessage("$label 계정이 연결되었습니다.")
        }
    }

    OutlinedButton(
        onClick = {
            val clientIdId = resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (clientIdId == 0) {
                onMessage("Google OAuth 웹 클라이언트가 아직 Firebase에 등록되지 않았습니다.")
                return@OutlinedButton
            }
            val clientId = resources.getString(clientIdId)
            onBusy(true)
            scope.launch {
                runCatching {
                    val option = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(clientId)
                        .setAutoSelectEnabled(false)
                        .build()
                    val result = CredentialManager.create(context).getCredential(
                        context,
                        GetCredentialRequest.Builder().addCredentialOption(option).build(),
                    )
                    val credential = result.credential
                    require(
                        credential is CustomCredential &&
                            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                    )
                    GoogleAuthProvider.getCredential(GoogleIdTokenCredential.createFrom(credential.data).idToken, null)
                }.fold(onSuccess = { credential ->
                    val task = if (currentUser == null) auth.signInWithCredential(credential) else currentUser.linkWithCredential(credential)
                    task.addOnCompleteListener {
                        onBusy(false)
                        if (!it.isSuccessful) onMessage("Google 인증을 완료하지 못했습니다.")
                        else onMessage("Google 계정이 연결되었습니다.")
                    }
                }, onFailure = {
                    onBusy(false)
                    onMessage("Google 인증이 취소되었거나 설정되지 않았습니다.")
                })
            }
        },
        enabled = !googleLinked,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (googleLinked) "Google · 연결됨" else "Google로 계속") }
    OutlinedButton(onClick = { oauth("github.com", "GitHub") }, enabled = !githubLinked, modifier = Modifier.fillMaxWidth()) {
        Text(if (githubLinked) "GitHub · 연결됨" else "GitHub로 계속")
    }
    listOf("kakao" to "Kakao", "naver" to "Naver").forEach { (provider, label) ->
    OutlinedButton(
        onClick = {
            fun start(token: String?) {
                val (verifier, challenge) = newNaverPkce()
                context.getSharedPreferences("auth_recovery", Context.MODE_PRIVATE).edit()
                    .putString("${provider}_code_verifier", verifier).apply()
                scope.launch {
                    StudentAccountRepository().startSocial(provider, token, challenge).fold(
                        onSuccess = { authorizeUrl ->
                            onBusy(false)
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authorizeUrl)))
                        },
                        onFailure = {
                            context.getSharedPreferences("auth_recovery", Context.MODE_PRIVATE).edit()
                                .remove("${provider}_code_verifier").apply()
                            onBusy(false)
                            onMessage(it.message ?: "$label 인증 서버를 열지 못했습니다.")
                        },
                    )
                }
            }
            onBusy(true)
            onMessage(null)
            if (currentUser == null) start(null)
            else currentUser.getIdToken(true).addOnCompleteListener { task ->
                val token = task.result?.token
                if (token == null) {
                    onBusy(false)
                    onMessage("로그인 정보를 갱신하지 못했습니다.")
                } else start(token)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("${label}로 계속")
    }
    }
}

@Composable
fun AuthenticationLoading(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LinearProgressIndicator(Modifier.fillMaxWidth(0.5f))
            Text(message, modifier = Modifier.padding(top = 14.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun accountAuthError(error: Throwable?): String = when {
    error?.message?.contains("already", ignoreCase = true) == true -> "이미 다른 계정에서 사용하는 이메일입니다."
    else -> "계정을 만들지 못했습니다. 이메일과 비밀번호를 확인해 주세요."
}

private fun newNaverPkce(): Pair<String, String> {
    val random = ByteArray(32).also(SecureRandom()::nextBytes)
    val verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(random)
    val challenge = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    return verifier to challenge
}
