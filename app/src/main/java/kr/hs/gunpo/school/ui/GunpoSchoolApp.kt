package kr.hs.gunpo.school.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.hs.gunpo.school.MainViewModel
import kr.hs.gunpo.school.data.AcademicEvent
import kr.hs.gunpo.school.data.EighthPeriodMode
import kr.hs.gunpo.school.data.Lesson
import kr.hs.gunpo.school.data.Meal
import kr.hs.gunpo.school.data.Notice
import kr.hs.gunpo.school.data.NeisState
import kr.hs.gunpo.school.data.parseStudentNumber
import kr.hs.gunpo.school.data.NoticeState
import kr.hs.gunpo.school.data.SchoolData
import kr.hs.gunpo.school.data.SupplementaryCourseCatalog
import kr.hs.gunpo.school.data.SupplementaryCourseGroup
import kr.hs.gunpo.school.data.UserSettings
import kr.hs.gunpo.school.domain.AcademicScheduleKind
import kr.hs.gunpo.school.domain.AcademicSchedulePolicy
import kr.hs.gunpo.school.domain.SchoolMoment
import kr.hs.gunpo.school.domain.SchoolAssistant
import kr.hs.gunpo.school.domain.HybridSchoolAssistant
import kr.hs.gunpo.school.domain.AssistantConversationTurn
import kr.hs.gunpo.school.domain.HomeMealPresentation
import kr.hs.gunpo.school.domain.HomeMealSelector
import kr.hs.gunpo.school.domain.NetworkClock
import kr.hs.gunpo.school.domain.SchoolTimeline
import kr.hs.gunpo.school.ui.theme.Navy
import kr.hs.gunpo.school.ui.theme.SchoolBlue
import kr.hs.gunpo.school.ui.theme.SchoolGreen
import kr.hs.gunpo.school.ui.theme.SchoolOrange
import kr.hs.gunpo.school.ui.theme.SchoolPurple
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private enum class MainTab(val label: String, val icon: ImageVector) {
    HOME("홈", Icons.Default.Home),
    TIMETABLE("시간표", Icons.Default.CalendarMonth),
    MEAL("급식", Icons.Default.Restaurant),
    NOTICE("공지", Icons.Default.Notifications),
    SETTINGS("설정", Icons.Default.Settings),
}

private fun effectiveMeals(neisState: NeisState): List<Meal> {
    if (neisState.meals.isEmpty()) return SchoolData.meals
    // Swift 원본에 등록된 석식은 학교가 제공한 방학 식단이므로 NEIS보다 우선한다.
    val swiftDinnerKeys = SchoolData.meals.filter { it.type == "석식" }.map { it.date to it.type }.toSet()
    val remoteMeals = neisState.meals.filter { (it.date to it.type) !in swiftDinnerKeys }
    val remoteKeys = remoteMeals.map { it.date to it.type }.toSet()
    return (remoteMeals + SchoolData.meals.filter { (it.date to it.type) !in remoteKeys })
        .sortedWith(compareBy<Meal> { it.date }.thenBy { it.type })
}

private fun effectiveEvents(settings: UserSettings, neisState: NeisState): List<AcademicEvent> =
    (neisState.events.ifEmpty { SchoolData.events })
        .filter { it.grades.isEmpty() || settings.grade in it.grades }
        .sortedBy { it.start }

private val homeEventKeywords = listOf(
    "개학", "입학", "졸업", "시험", "평가", "수능", "체육", "축제", "행사", "상담", "설명회",
)

private fun isHomeHighlight(event: AcademicEvent): Boolean =
    homeEventKeywords.any { keyword -> event.title.contains(keyword) }

private fun effectiveLessons(date: LocalDate, settings: UserSettings, neisState: NeisState): List<Lesson> {
    AcademicSchedulePolicy.overrideFor(date, effectiveEvents(settings, neisState))?.let { return it.lessons }
    if (SchoolData.isVacation(date)) return SchoolData.lessonsFor(date, settings)
    if (neisState.grade != settings.grade || neisState.classNumber != settings.classNumber) {
        return SchoolData.lessonsFor(date, settings)
    }
    val remote = neisState.timetableByDate[date]
    if (remote != null) {
        val additional = SchoolData.additionalLessonsFor(date, settings)
            .filter { extra -> remote.none { it.period == extra.period } }
        return remote + additional
    }
    val monthWasSynced = neisState.timetableByDate.keys.any { it.year == date.year && it.month == date.month }
    return if (monthWasSynced) emptyList() else SchoolData.lessonsFor(date, settings)
}

@Composable
fun GunpoSchoolApp(viewModel: MainViewModel, startDestination: String? = null) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val neisState by viewModel.neisState.collectAsStateWithLifecycle()
    val noticeState by viewModel.noticeState.collectAsStateWithLifecycle()
    var networkNow by remember { mutableStateOf(NetworkClock.now()) }
    LaunchedEffect(Unit) {
        NetworkClock.synchronize()
        networkNow = NetworkClock.now()
        var loadedDate = networkNow.toLocalDate()
        var secondsUntilSync = 15 * 60
        while (true) {
            delay(1_000)
            networkNow = NetworkClock.now()
            val currentDate = networkNow.toLocalDate()
            if (currentDate != loadedDate) {
                loadedDate = currentDate
                viewModel.loadNeisForMonth(currentDate)
            }
            secondsUntilSync--
            if (secondsUntilSync <= 0) {
                NetworkClock.synchronize()
                networkNow = NetworkClock.now()
                secondsUntilSync = 15 * 60
            }
        }
    }
    val context = LocalContext.current
    var showAlwaysLocationGuide by remember { mutableStateOf(false) }
    val foregroundLocationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        showAlwaysLocationGuide = true
    }
    val requestLocationPermission = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            showAlwaysLocationGuide = true
        } else {
            foregroundLocationPermission.launch(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
            )
        }
    }
    val initialNotificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        requestLocationPermission()
    }
    val requestInitialPermissions = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission()
        } else {
            initialNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (showAlwaysLocationGuide) {
        AlertDialog(
            onDismissRequest = { showAlwaysLocationGuide = false },
            title = { Text("위치 권한을 확인해 주세요") },
            text = {
                Text("학교 도착 여부와 정확한 위치를 확인하려면 앱 권한 설정에서 위치를 ‘정확한 위치 사용’ 및 ‘항상 허용’으로 변경해 주세요.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAlwaysLocationGuide = false
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    },
                ) { Text("권한 설정 열기") }
            },
            dismissButton = {
                TextButton(onClick = { showAlwaysLocationGuide = false }) { Text("나중에") }
            },
        )
    }

    if (!settings.isLoaded) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LinearProgressIndicator(Modifier.width(160.dp))
            }
        }
        return
    }
    if (!settings.isProfileConfigured) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            ProfileSettings(
                settings,
                viewModel,
                padding,
                onBack = null,
                isInitialSetup = true,
                onInitialSetupComplete = requestInitialPermissions,
            )
        }
        return
    }

    var selectedTab by remember(startDestination) { mutableStateOf(when (startDestination) { "timetable" -> MainTab.TIMETABLE; "meal" -> MainTab.MEAL; else -> MainTab.HOME }) }
    var openAssistant by remember(startDestination) { mutableStateOf(startDestination == "assistant") }
    var homeSelectionVersion by remember { mutableIntStateOf(0) }
    var lastBackPressedAt by remember { mutableLongStateOf(0L) }

    BackHandler {
        if (selectedTab != MainTab.HOME) {
            openAssistant = false
            homeSelectionVersion++
            selectedTab = MainTab.HOME
        } else {
            val pressedAt = System.currentTimeMillis()
            if (pressedAt - lastBackPressedAt <= 2_000L) {
                (context as? Activity)?.finish()
            } else {
                lastBackPressedAt = pressedAt
                Toast.makeText(context, "뒤로 버튼을 한 번 더 누르면 앱이 종료됩니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = {
                            if (tab == MainTab.HOME) {
                                openAssistant = false
                                homeSelectionVersion++
                            }
                            selectedTab = tab
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        when (selectedTab) {
            MainTab.HOME -> HomeScreen(settings, neisState, noticeState, networkNow, innerPadding, openAssistant, homeSelectionVersion) { selectedTab = it }
            MainTab.TIMETABLE -> TimetableScreen(settings, neisState, networkNow, innerPadding, viewModel::loadNeisForWeek)
            MainTab.MEAL -> MealScreen(settings, neisState, networkNow.toLocalDate(), innerPadding, viewModel::loadNeisForMonth)
            MainTab.NOTICE -> NoticeScreen(noticeState, viewModel::refreshNotices, innerPadding)
            MainTab.SETTINGS -> SettingsScreen(settings, neisState, viewModel, innerPadding)
        }
    }
}

@Composable
private fun NavigationHeader(
    title: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    onBack: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(start = if (onBack == null) 18.dp else 4.dp, end = 18.dp, top = 13.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        badge?.let { Badge(it, SchoolBlue) }
    }
}

@Composable
private fun HomeScreen(settings: UserSettings, neisState: NeisState, noticeState: NoticeState, now: LocalDateTime, padding: PaddingValues, openChatInitially: Boolean = false, homeSelectionVersion: Int = 0, selectTab: (MainTab) -> Unit) {
    var showChat by remember(openChatInitially) { mutableStateOf(openChatInitially) }
    var showAllEvents by remember { mutableStateOf(false) }
    var showClassAlign by remember { mutableStateOf(false) }
    LaunchedEffect(homeSelectionVersion) {
        if (homeSelectionVersion > 0) {
            showChat = false
            showAllEvents = false
            showClassAlign = false
        }
    }
    BackHandler(enabled = showChat || showAllEvents || showClassAlign) {
        when {
            showChat -> showChat = false
            showAllEvents -> showAllEvents = false
            showClassAlign -> showClassAlign = false
        }
    }
    if (showChat) {
        SchoolAssistantScreen(settings, neisState, padding) { showChat = false }
        return
    }
    if (showAllEvents) {
        AcademicEventsScreen(settings, neisState, now.toLocalDate(), padding) { showAllEvents = false }
        return
    }
    if (showClassAlign) {
        ClassAlignScreen(padding) { showClassAlign = false }
        return
    }
    val lessons = effectiveLessons(now.toLocalDate(), settings, neisState)
    val moment = SchoolTimeline.moment(now, lessons)
    val mealPresentation = HomeMealSelector.select(now, effectiveMeals(neisState))
    val events = effectiveEvents(settings, neisState)
        .filter { !it.end.isBefore(now.toLocalDate()) }
        .filter(::isHomeHighlight)
        .take(3)

    Column(Modifier.fillMaxSize().padding(padding)) {
        NavigationHeader("군포고등학교", badge = settings.className)
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item { ProfileHeader(settings, now) }
                item { StatusCard(moment) }
                item { QuickMenu(selectTab, onClassAlign = { showClassAlign = true }) }
                item { TodayScheduleCard(lessons, moment, onAll = { selectTab(MainTab.TIMETABLE) }) }
                item { TodayMealCard(mealPresentation, onAll = { selectTab(MainTab.MEAL) }) }
                item { UpcomingEventsCard(events) { showAllEvents = true } }
                item { RecentNoticesCard(noticeState.notices.ifEmpty { SchoolData.notices }, onAll = { selectTab(MainTab.NOTICE) }) }
            }
            Surface(
                onClick = { showChat = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 18.dp).shadow(10.dp, CircleShape),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
                border = BorderStroke(1.dp, SchoolBlue.copy(alpha = .25f)),
            ) {
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.Chat, "Gemini 열기", tint = SchoolBlue, modifier = Modifier.size(23.dp))
                }
            }
        }
    }
}

private data class ChatMessage(val text: String, val fromUser: Boolean, val isThinking: Boolean = false)

@Composable
private fun SchoolAssistantScreen(settings: UserSettings, neisState: NeisState, padding: PaddingValues, onDismiss: () -> Unit) {
    val messages = remember { mutableStateListOf(ChatMessage("안녕하세요! Gemini예요. 학교생활 정보와 일반 질문 모두 물어보세요.", false)) }
    var input by remember { mutableStateOf("") }
    var isAnswering by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val messageListState = rememberLazyListState()
    val suggestions = listOf("오늘 시간표", "오늘 급식", "지금 몇 교시야?", "오늘 야자")
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val messageMaxWidth = if (screenWidthDp >= 600) (screenWidthDp * .72f).dp else 310.dp

    fun send(text: String) {
        if (text.isBlank() || isAnswering) return
        val history = messages.drop(1).windowed(2, 2, partialWindows = false).mapNotNull { pair ->
            val user = pair.getOrNull(0)
            val assistant = pair.getOrNull(1)
            if (user?.fromUser == true && assistant != null && !assistant.fromUser && !assistant.isThinking) {
                AssistantConversationTurn(user.text, assistant.text)
            } else null
        }.takeLast(4)
        messages += ChatMessage(text.trim(), true)
        messages += ChatMessage("", false, isThinking = true)
        val answerIndex = messages.lastIndex
        input = ""
        isAnswering = true
        scope.launch {
            val now = NetworkClock.now()
            val factual = SchoolAssistant.answer(text, now, settings, effectiveLessons(now.toLocalDate(), settings, neisState), effectiveMeals(neisState).filter { it.date == now.toLocalDate() })
            val result = HybridSchoolAssistant.answer(text, factual, SchoolAssistant.isSchoolQuestion(text), history)
            messages[answerIndex] = ChatMessage("", false)
            result.text.forEachIndexed { index, character ->
                messages[answerIndex] = ChatMessage(result.text.substring(0, index + 1), false)
                delay(if (character in listOf('.', '!', '?', '。', '\n')) 65 else 14)
            }
            isAnswering = false
        }
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length?.div(12)) {
        if (messages.isNotEmpty()) messageListState.animateScrollToItem(messages.lastIndex)
    }
    Surface(Modifier.fillMaxSize().padding(padding), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            NavigationHeader("Gemini", badge = "Gemini", onBack = onDismiss)
            LazyColumn(
                state = messageListState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(messages) { _, message ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
                        Surface(
                            color = if (message.fromUser) SchoolBlue else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(18.dp),
                            shadowElevation = if (message.fromUser) 0.dp else 1.dp,
                            modifier = Modifier.widthIn(max = messageMaxWidth),
                        ) {
                            if (message.isThinking) ThinkingIndicator()
                            else Text(message.text, Modifier.padding(horizontal = 15.dp, vertical = 11.dp), color = if (message.fromUser) Color.White else MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        }
                    }
                }
            }
            LazyColumn(contentPadding = PaddingValues(horizontal = 14.dp), modifier = Modifier.heightIn(max = 54.dp)) {
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        suggestions.forEach { suggestion -> FilterChip(selected = false, onClick = { send(suggestion) }, label = { Text(suggestion, fontSize = 11.sp) }) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) {
                            send(input)
                            true
                        } else false
                    },
                    placeholder = { Text("Gemini에게 질문하기") },
                    singleLine = false,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send(input) }),
                )
                Button(onClick = { send(input) }, enabled = input.isNotBlank() && !isAnswering) { Text(if (isAnswering) "답변 중" else "전송") }
            }
        }
    }
}
@Composable
private fun ThinkingIndicator() {
    val transition = rememberInfiniteTransition(label = "Gemini thinking")
    val phase by transition.animateFloat(initialValue = 0f, targetValue = 3f, animationSpec = infiniteRepeatable(animation = tween(900)), label = "Thinking dots")
    Row(Modifier.padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { index ->
            Box(Modifier.size(7.dp).alpha(if (phase.toInt().coerceIn(0, 2) == index) 1f else .28f).background(SchoolBlue, CircleShape))
        }
        Text("Gemini가 답변을 생각하고 있어요", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
    }
}
@Composable
private fun ProfileHeader(settings: UserSettings, now: LocalDateTime) {
    Column(
        Modifier.fillMaxWidth().shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = SchoolBlue.copy(alpha = .22f), spotColor = SchoolBlue.copy(alpha = .22f))
            .clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Navy, SchoolBlue))).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("${settings.studentNumber} ${settings.studentName}", color = Color.White.copy(alpha = .82f), fontSize = 15.sp)
                Text(settings.className, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            }
            Surface(shape = CircleShape, color = Color.White.copy(alpha = .14f)) {
                Box(Modifier.size(62.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AccountBalance, null, tint = Color.White, modifier = Modifier.size(34.dp))
                }
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = .3f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CalendarMonth, null, tint = Color.White.copy(alpha = .9f), modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(now.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 EEEE", Locale.KOREAN)), color = Color.White.copy(alpha = .9f), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm:ss")), color = Color.White.copy(alpha = .9f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private data class StatusUi(val label: String, val subject: String, val detail: String, val next: String?, val active: Boolean)

private fun statusUi(moment: SchoolMoment): StatusUi = when (moment) {
    is SchoolMoment.InClass -> StatusUi("LIVE · ${moment.lesson.period}교시", moment.lesson.subject, "${timeRange(moment.lesson)} · ${moment.lesson.room}", moment.next?.let { "${it.period}교시 · ${it.subject}" } ?: "오늘 수업 완료", true)
    is SchoolMoment.BetweenClasses -> StatusUi("쉬는시간", "${moment.next.startMinute - moment.previous.endMinute}분 휴식", "다음 교시 준비", "${moment.next.period}교시 · ${moment.next.subject}", false)
    is SchoolMoment.BeforeSchool -> StatusUi("등교 전", "수업 준비", "${SchoolTimeline.clock(moment.next.startMinute)} 시작", "${moment.next.period}교시 · ${moment.next.subject}", false)
    is SchoolMoment.Finished -> StatusUi("수업 완료", "오늘 수업 완료", "${moment.last.period}교시까지 수고했어요", null, false)
    SchoolMoment.NoSchool -> StatusUi("수업 없음", "오늘은 수업이 없습니다", "주말 또는 휴일", null, false)
}

@Composable
private fun StatusCard(moment: SchoolMoment) {
    val status = statusUi(moment)
    val color = if (status.active) SchoolGreen else SchoolBlue
    SchoolCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = CircleShape, color = color.copy(alpha = .14f)) {
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    Icon(if (status.active) Icons.Default.Timer else Icons.Default.CheckCircle, null, tint = color, modifier = Modifier.size(25.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(status.label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(status.subject, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(status.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            status.next?.let { next ->
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("다음", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    Text(next, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                }
            }
        }
    }
}

@Composable
private fun QuickMenu(selectTab: (MainTab) -> Unit, onClassAlign: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickButton("시간표", Icons.Default.Schedule, SchoolBlue, Modifier.weight(1f)) { selectTab(MainTab.TIMETABLE) }
            QuickButton("오늘 급식", Icons.Default.Restaurant, SchoolGreen, Modifier.weight(1f)) { selectTab(MainTab.MEAL) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickButton("공지사항", Icons.Default.Notifications, SchoolOrange, Modifier.weight(1f)) { selectTab(MainTab.NOTICE) }
            QuickButton("개인 일정 설정", Icons.Default.Settings, SchoolPurple, Modifier.weight(1f)) { selectTab(MainTab.SETTINGS) }
        }
        Row {
            QuickButton("분반 확인", Icons.AutoMirrored.Filled.OpenInNew, SchoolBlue, Modifier.weight(1f), onClassAlign)
        }
    }
}

@Composable
private fun QuickButton(title: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.shadow(3.dp, RoundedCornerShape(18.dp)), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, border = schoolBorder()) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Surface(shape = RoundedCornerShape(11.dp), color = color.copy(alpha = .12f)) {
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)) }
            }
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun TodayScheduleCard(lessons: List<Lesson>, moment: SchoolMoment, onAll: () -> Unit) {
    val current = (moment as? SchoolMoment.InClass)?.lesson?.period
    SchoolCard {
        SectionHeader("오늘의 시간표", Icons.Default.Schedule, "전체보기", onAll)
        Spacer(Modifier.height(14.dp))
        if (lessons.isEmpty()) Text("오늘은 수업이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        else lessons.forEachIndexed { index, lesson ->
            LessonLine(lesson, current == lesson.period)
            if (index != lessons.lastIndex) HorizontalDivider(Modifier.padding(start = 42.dp, top = 8.dp, bottom = 8.dp))
        }
    }
}

@Composable
private fun TodayMealCard(presentation: HomeMealPresentation, onAll: () -> Unit) {
    val meal = presentation.meal
    SchoolCard {
        SectionHeader(presentation.title, Icons.Default.Restaurant, "식단표", onAll)
        Spacer(Modifier.height(14.dp))
        presentation.notice?.let {
            Text(it, color = SchoolBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
        }
        if (meal == null) {
            val emptyMessage = if (presentation.notice != null) {
                "내일 중식 정보가 없습니다."
            } else {
                "오늘은 등록된 ${presentation.type} 정보가 없습니다."
            }
            Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        } else {
            Text(meal.menu.take(3).joinToString(" · "), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (meal.menu.size > 3) Text(meal.menu.drop(3).joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(top = 7.dp))
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge(meal.type, SchoolOrange)
                if (meal.calories.isNotBlank()) Badge(meal.calories, SchoolGreen)
            }
        }
    }
}

@Composable
private fun UpcomingEventsCard(events: List<AcademicEvent>, onAll: () -> Unit) {
    SchoolCard {
        SectionHeader("다가오는 학사일정", Icons.Default.CalendarMonth, "전체보기", onAll)
        Spacer(Modifier.height(13.dp))
        if (events.isEmpty()) Text("남은 학사일정이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        else events.forEachIndexed { index, event ->
            EventLine(event)
            if (index != events.lastIndex) HorizontalDivider(Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun AcademicEventsScreen(settings: UserSettings, neisState: NeisState, today: LocalDate, padding: PaddingValues, onBack: () -> Unit) {
    val allEvents = effectiveEvents(settings, neisState).sortedBy { it.start }
    val events = allEvents.filter { !it.end.isBefore(today) }.groupBy { it.start.monthValue }
    Column(Modifier.fillMaxSize().padding(padding)) {
        NavigationHeader("2026 학사일정", badge = if (neisState.events.isNotEmpty()) "NEIS" else "오프라인", onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (events.isEmpty()) {
                item {
                    Text("오늘 이후 등록된 학사일정이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                }
            }
            events.forEach { (month, monthEvents) ->
                item {
                    SettingsSection("${month}월") {
                        monthEvents.forEachIndexed { index, event ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                EventLine(event, Modifier.weight(1f))
                                if (event.scope != "전학년") Badge(event.scope, if (event.scope == "공휴일") SchoolOrange else SchoolBlue, compact = true)
                            }
                            if (index != monthEvents.lastIndex) HorizontalDivider(Modifier.padding(start = 68.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassAlignScreen(padding: PaddingValues, onBack: () -> Unit) = InternalWebScreen(
    padding = padding,
    title = "분반 확인",
    badge = "Class Align",
    initialUrl = "https://class-align.vercel.app/",
    allowedHosts = setOf("class-align.vercel.app"),
    onBack = onBack,
)

@Composable
private fun InternalWebScreen(
    padding: PaddingValues,
    title: String,
    badge: String,
    initialUrl: String,
    allowedHosts: Set<String>,
    onBack: () -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    fun navigateBack() {
        val current = webView
        if (current?.canGoBack() == true) current.goBack() else onBack()
    }

    BackHandler(onBack = ::navigateBack)
    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }

    Column(Modifier.fillMaxSize().padding(padding)) {
        NavigationHeader(title, badge = badge, onBack = ::navigateBack)
        if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    webView = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.setSupportZoom(false)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val uri = request.url
                            val host = uri.host.orEmpty()
                            val isAllowed = uri.scheme == "https" && allowedHosts.any { host == it || host.endsWith(".$it") }
                            return if (isAllowed) {
                                false
                            } else {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                                true
                            }
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            isLoading = false
                        }
                    }
                    loadUrl(initialUrl)
                }
            },
        )
    }
}

@Composable
private fun EventLine(event: AcademicEvent, modifier: Modifier = Modifier) {
    val color = if (event.scope == "공휴일") SchoolOrange else SchoolBlue
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(11.dp), color = color.copy(alpha = .1f)) {
            Column(Modifier.size(42.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("${event.start.dayOfMonth}", color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(event.start.dayOfWeek.koreanShort(), color = color, fontSize = 10.sp)
            }
        }
        Text("${event.start.monthValue}월 · ${event.title}", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RecentNoticesCard(notices: List<Notice>, onAll: () -> Unit) {
    SchoolCard {
        SectionHeader("최근 학교 소식", Icons.Default.Campaign, "더보기", onAll)
        Spacer(Modifier.height(14.dp))
        notices.take(3).forEachIndexed { index, notice ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Badge(notice.category, SchoolBlue, compact = true)
                Text(notice.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (notice.isNew) Box(Modifier.size(6.dp).background(Color.Red, CircleShape))
                Text(notice.dateLabel.takeLast(5), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
            if (index != notices.take(3).lastIndex) HorizontalDivider(Modifier.padding(vertical = 10.dp))
        }
    }
}

@Composable
private fun TimetableScreen(
    settings: UserSettings,
    neisState: NeisState,
    now: LocalDateTime,
    padding: PaddingValues,
    loadWeek: (LocalDate) -> Unit,
) {
    val today = now.toLocalDate()
    val todayIndex = (today.dayOfWeek.value - 1).coerceIn(0, 4)
    val defaultDate = SchoolTimeline.defaultTimetableDate(now, effectiveLessons(today, settings, neisState))
    val startsOnNextMonday = defaultDate != today
    var selectedIndex by remember(today) { mutableIntStateOf(if (startsOnNextMonday) 0 else todayIndex) }
    var weekOffset by remember(today) { mutableIntStateOf(if (startsOnNextMonday) 1 else 0) }
    var automaticAdvanceApplied by remember(today) { mutableStateOf(startsOnNextMonday) }
    val currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val monday = currentMonday.plusWeeks(weekOffset.toLong())
    val friday = monday.plusDays(4)
    val selectedDate = monday.plusDays(selectedIndex.toLong())
    val scheduleOverride = AcademicSchedulePolicy.overrideFor(selectedDate, effectiveEvents(settings, neisState))
    val lessons = effectiveLessons(selectedDate, settings, neisState)
    val isVacation = SchoolData.isVacation(selectedDate)
    val isTemporaryTimetable = selectedDate in neisState.temporaryTimetableDates
    val selectedEvent = effectiveEvents(settings, neisState).firstOrNull { selectedDate in it.start..it.end }
    val current = if (selectedDate == today) (SchoolTimeline.moment(now, lessons) as? SchoolMoment.InClass)?.lesson?.period else null
    val timetableBadge = when (scheduleOverride?.kind) {
        AcademicScheduleKind.HOLIDAY_SELF_STUDY -> "공휴일"
        AcademicScheduleKind.STUDY_ROOM_CLOSED -> "운영 안 함"
        AcademicScheduleKind.ACADEMIC_ASSESSMENT -> "학력평가"
        null -> when (neisState.timetableSource) {
            "temporary" -> "임시"
            "mixed" -> "NEIS·임시"
            else -> if (neisState.timetableByDate.isNotEmpty()) "NEIS" else null
        }
    }
    LaunchedEffect(monday) { loadWeek(monday) }
    LaunchedEffect(defaultDate) {
        if (!automaticAdvanceApplied && defaultDate != today) {
            selectedIndex = 0
            weekOffset = 1
            automaticAdvanceApplied = true
        }
    }

    Column(Modifier.fillMaxSize().padding(padding)) {
        NavigationHeader("주간 시간표", badge = timetableBadge)
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SchoolCard(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { weekOffset-- }) {
                            Icon(Icons.Default.ChevronLeft, "이전 주")
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${monday.format(DateTimeFormatter.ofPattern("M월 d일"))} – ${friday.format(DateTimeFormatter.ofPattern("M월 d일"))}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            if (weekOffset == 0) {
                                Text("이번 주", color = SchoolBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            } else {
                                TextButton(onClick = { weekOffset = 0 }, contentPadding = PaddingValues(0.dp)) {
                                    Text("이번 주로 이동", color = SchoolBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        IconButton(onClick = {
                            weekOffset++
                            selectedIndex = 0
                        }) {
                            Icon(Icons.Default.ChevronRight, "다음 주")
                        }
                    }
                    if (neisState.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            item { SegmentedDays(selectedIndex) { selectedIndex = it } }
            item {
                SchoolCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                if (isVacation) "여름방학 ${SchoolData.days[selectedIndex].longName}" else "${if (selectedDate.monthValue >= 8) 2 else 1}학기 ${SchoolData.days[selectedIndex].longName} · ${selectedDate.format(DateTimeFormatter.ofPattern("M월 d일"))}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                when (scheduleOverride?.kind) {
                                    AcademicScheduleKind.HOLIDAY_SELF_STUDY -> "${scheduleOverride.eventTitle} · 전 교시 자습"
                                    AcademicScheduleKind.STUDY_ROOM_CLOSED -> "${scheduleOverride.eventTitle} · 자습실 미운영"
                                    AcademicScheduleKind.ACADEMIC_ASSESSMENT -> "${scheduleOverride.eventTitle} · 시험 시간표"
                                    null -> if (isVacation) "70분 수업 · 선택 과목 및 강의실" else if (neisState.timetableByDate.containsKey(selectedDate)) {
                                        val source = if (isTemporaryTimetable) "임시 시간표" else "NEIS 시간표"
                                        "$source · ${settings.grade}학년 ${settings.classNumber}반"
                                    } else "저장된 시간표 · 7교시"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                        Badge(
                            if (scheduleOverride?.kind == AcademicScheduleKind.STUDY_ROOM_CLOSED) "운영 안 함"
                            else if (lessons.isEmpty()) "수업 없음" else "${lessons.size}교시",
                            if (scheduleOverride?.kind == AcademicScheduleKind.STUDY_ROOM_CLOSED) SchoolOrange else SchoolBlue,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    if (lessons.isEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Flag, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (scheduleOverride?.kind == AcademicScheduleKind.STUDY_ROOM_CLOSED) scheduleOverride.eventTitle
                                else selectedEvent?.title ?: "수업 정보 없음",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 7.dp),
                            )
                            Text(
                                when {
                                    scheduleOverride?.kind == AcademicScheduleKind.STUDY_ROOM_CLOSED -> "오늘은 자습실을 운영하지 않습니다."
                                    neisState.isLoading -> "시간표를 불러오는 중입니다."
                                    selectedEvent != null -> "${selectedEvent.scope} 학사일정입니다."
                                    else -> "이 날짜에 등록된 수업이 없습니다."
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                            )
                        }
                    } else lessons.forEachIndexed { index, lesson ->
                        LessonLine(lesson, current == lesson.period)
                        if (index != lessons.lastIndex) HorizontalDivider(Modifier.padding(start = 42.dp, top = 8.dp, bottom = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentedDays(selected: Int, onSelected: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(2.dp)) {
        SchoolData.days.forEachIndexed { index, day ->
            Surface(
                onClick = { onSelected(index) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(7.dp),
                color = if (selected == index) MaterialTheme.colorScheme.surface else Color.Transparent,
                shadowElevation = if (selected == index) 2.dp else 0.dp,
            ) { Text(day.shortName, Modifier.padding(vertical = 7.dp), textAlign = TextAlign.Center, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun LessonLine(lesson: Lesson, isCurrent: Boolean) {
    val color = if (isCurrent) SchoolGreen else SchoolBlue
    Row(
        Modifier.fillMaxWidth().then(if (isCurrent) Modifier.background(SchoolGreen.copy(alpha = .08f), RoundedCornerShape(12.dp)).border(1.dp, SchoolGreen.copy(alpha = .25f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 7.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(shape = CircleShape, color = color) {
            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { Text("${lesson.period}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(lesson.subject, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(lesson.room, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                if (isCurrent) Badge("현재 수업", SchoolGreen, compact = true)
            }
        }
        Text(timeRange(lesson), color = if (isCurrent) SchoolGreen else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun MealScreen(settings: UserSettings, neisState: NeisState, today: LocalDate, padding: PaddingValues, loadMonth: (LocalDate) -> Unit) {
    val meals = effectiveMeals(neisState)
    var selected by remember(today) { mutableStateOf(today) }
    var displayedMonth by remember(today) { mutableStateOf(YearMonth.from(today)) }
    val calendarDates = remember(displayedMonth) {
        val first = displayedMonth.atDay(1)
        val start = first.minusDays((first.dayOfWeek.value % 7).toLong())
        (0 until 42).map { start.plusDays(it.toLong()) }
    }
    val selectedMeals = meals.filter { it.date == selected }.associateBy { it.type }

    fun changeMonth(month: YearMonth) {
        displayedMonth = month
        selected = if (month == YearMonth.from(today)) today else month.atDay(1)
        loadMonth(month.atDay(1))
    }

    Column(Modifier.fillMaxSize().padding(padding)) {
        NavigationHeader("급식 달력", badge = if (neisState.meals.isNotEmpty()) "NEIS" else "오프라인")
        LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                SchoolCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { changeMonth(displayedMonth.minusMonths(1)) }) {
                            Icon(Icons.Default.ChevronLeft, "이전 달")
                        }
                        Text(
                            displayedMonth.atDay(1).format(DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { changeMonth(displayedMonth.plusMonths(1)) }) {
                            Icon(Icons.Default.ChevronRight, "다음 달")
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    CalendarGrid(calendarDates, displayedMonth, selected, today, meals) { date ->
                        if (YearMonth.from(date) != displayedMonth) changeMonth(YearMonth.from(date))
                        selected = date
                    }
                }
            }
            item {
                SchoolCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(selected.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (selected == today) Badge("오늘", SchoolBlue)
                    }
                    Spacer(Modifier.height(12.dp))
                    val selectedEvent = effectiveEvents(settings, neisState).firstOrNull { selected in it.start..it.end }
                    listOf("중식", "석식").forEachIndexed { index, type ->
                        selectedMeals[type]?.let { MealBlock(it) } ?: UnavailableMealBlock(type, selected, selectedEvent, today)
                        if (index == 0) HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarGrid(dates: List<LocalDate>, displayedMonth: YearMonth, selected: LocalDate, today: LocalDate, mealsData: List<Meal>, onSelect: (LocalDate) -> Unit) {
    val symbols = listOf("일", "월", "화", "수", "목", "금", "토")
    Row(Modifier.fillMaxWidth()) {
        symbols.forEachIndexed { index, symbol ->
            Text(symbol, color = when (index) { 0 -> Color.Red.copy(alpha = .78f); 6 -> SchoolBlue.copy(alpha = .78f); else -> MaterialTheme.colorScheme.onSurface }, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(7.dp))
    dates.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            week.forEach { date ->
                val meals = mealsData.filter { it.date == date }
                val isSelected = date == selected
                Surface(
                    onClick = { onSelect(date) },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    shape = RoundedCornerShape(11.dp),
                    color = if (isSelected) SchoolBlue else Color.Transparent,
                    border = if (date == today && !isSelected) BorderStroke(1.5.dp, SchoolBlue) else null,
                ) {
                    Column(Modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(if (date.dayOfMonth == 1) "${date.monthValue}월" else " ", color = if (isSelected) Color.White.copy(alpha = .8f) else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                        Text("${date.dayOfMonth}", color = when { isSelected -> Color.White; YearMonth.from(date) != displayedMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .32f); else -> MaterialTheme.colorScheme.onSurface }, fontSize = 16.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            if (meals.any { it.type == "중식" }) Box(Modifier.size(4.dp).background(if (isSelected) Color.White else SchoolGreen, CircleShape))
                            if (meals.any { it.type == "석식" }) Box(Modifier.size(4.dp).background(if (isSelected) Color.White.copy(alpha = .8f) else SchoolOrange, CircleShape))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(7.dp))
    }
}

@Composable
private fun UnavailableMealBlock(type: String, date: LocalDate, event: AcademicEvent?, today: LocalDate) {
    val color = if (type == "석식") SchoolOrange else SchoolGreen
    val isWeekday = date.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    val pendingMealInfoStart = LocalDate.of(today.year, 8, 13)
    val isPendingMealInfo = date.year == today.year &&
        !date.isBefore(pendingMealInfoStart) && date.isAfter(today)
    val reason = when {
        isWeekday && isPendingMealInfo && type == "중식" -> "중식은 아직 NEIS 정보가 없습니다."
        isWeekday && isPendingMealInfo && type == "석식" -> "석식은 아직 식단표가 나오지 않았습니다."
        type == "석식" && isWeekday && event != null -> "학사일정 ‘${event.title}’로 석식이 제공되지 않습니다."
        else -> "해당 날에는 $type 제공이 되지 않습니다."
    }
    Column(Modifier.fillMaxWidth().background(color.copy(alpha = .07f), RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Badge(type, color)
        Text("$type: $reason", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}

@Composable
private fun MealBlock(meal: Meal) {
    val color = if (meal.type == "석식") SchoolOrange else SchoolGreen
    Column(Modifier.fillMaxWidth().background(color.copy(alpha = .07f), RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(meal.type, color)
            Spacer(Modifier.weight(1f))
            Text(meal.calories, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Text(meal.menu.joinToString(" · "), fontSize = 14.sp)
    }
}

@Composable
private fun NoticeScreen(noticeState: NoticeState, refresh: () -> Unit, padding: PaddingValues) {
    var selectedNotice by remember { mutableStateOf<Notice?>(null) }
    selectedNotice?.let { notice ->
        InternalWebScreen(
            padding = padding,
            title = notice.section,
            badge = "학교 홈페이지",
            initialUrl = notice.url,
            allowedHosts = setOf("gunpo.hs.kr"),
            onBack = { selectedNotice = null },
        )
        return
    }
    val notices = noticeState.notices.ifEmpty { SchoolData.notices }
    val family = notices.filter { it.section == "가정통신문" }
    val general = notices.filter { it.section == "공지사항" }
    Column(Modifier.fillMaxSize().padding(padding)) {
        NavigationHeader(
            "학교 공지",
            badge = when {
                noticeState.isLoading -> "불러오는 중"
                noticeState.errorMessage != null -> "오프라인"
                noticeState.isFromCache -> "저장됨"
                else -> "실시간"
            },
        )
        if (noticeState.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        noticeState.errorMessage ?: "군포고등학교 홈페이지에서 최신 목록을 가져옵니다.",
                        color = if (noticeState.errorMessage == null) MaterialTheme.colorScheme.onSurfaceVariant else SchoolOrange,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = refresh, enabled = !noticeState.isLoading) {
                        Icon(Icons.Default.Sync, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("새로고침")
                    }
                }
            }
            item { NoticeSection("가정통신문", family) { selectedNotice = it } }
            item { NoticeSection("공지사항", general) { selectedNotice = it } }
        }
    }
}

@Composable
private fun NoticeSection(title: String, notices: List<Notice>, open: (Notice) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp))
        SchoolCard(contentPadding = PaddingValues(0.dp)) {
            notices.forEachIndexed { index, notice ->
                Row(
                    Modifier.fillMaxWidth().clickable { open(notice) }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    Icon(if (notice.category == "공지") Icons.Default.Campaign else Icons.AutoMirrored.Filled.MenuBook, null, tint = SchoolBlue, modifier = Modifier.width(28.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(notice.title, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(notice.dateLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    }
                    Icon(Icons.Default.ChevronRight, "앱 안에서 열기", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
                if (index != notices.lastIndex) HorizontalDivider(Modifier.padding(start = 57.dp))
            }
        }
    }
}

@Composable
private fun SettingsScreen(settings: UserSettings, neisState: NeisState, viewModel: MainViewModel, padding: PaddingValues) {
    var detail by remember { mutableStateOf(false) }
    var profileDetail by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.updateLiveUpdates(true)
    }
    val fineLocationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }
    }
    BackHandler(enabled = profileDetail || detail) {
        if (profileDetail) profileDetail = false else detail = false
    }
    if (profileDetail) {
        ProfileSettings(settings, viewModel, padding, onBack = { profileDetail = false })
    } else if (detail) {
        ScheduleSettings(settings, viewModel, padding) { detail = false }
    } else Column(Modifier.fillMaxSize().padding(padding)) {
        NavigationHeader("내 학교생활 설정")
        LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                SettingsSection("학생 정보") {
                    SettingsRow(
                        Icons.Default.Person,
                        "${settings.studentNumber} ${settings.studentName}",
                        settings.className,
                        SchoolBlue,
                    )
                    HorizontalDivider(Modifier.padding(start = 54.dp))
                    SettingsActionRow(Icons.Default.Tune, "이름 · 학번 · 학년 · 반 설정", showChevron = true) {
                        profileDetail = true
                    }
                }
            }
            item {
                SettingsSection("NEIS 데이터") {
                    SettingsRow(
                        Icons.Default.Sync,
                        when {
                            neisState.isLoading -> "NEIS 동기화 중"
                            neisState.errorMessage != null -> "오프라인 데이터 사용 중"
                            neisState.isFromCache -> "저장된 NEIS 데이터 사용 중"
                            else -> "NEIS 데이터 최신 상태"
                        },
                        neisState.errorMessage ?: "급식 · 학사일정 · ${settings.grade}학년 ${settings.classNumber}반 시간표",
                        if (neisState.errorMessage == null) SchoolGreen else SchoolOrange,
                    )
                    HorizontalDivider(Modifier.padding(start = 54.dp))
                    SettingsActionRow(Icons.Default.Sync, "지금 새로고침") { viewModel.refreshNeis() }
                }
            }
            item {
                SettingsSection("실시간 수업 상태") {
                    SettingsRow(
                        Icons.Default.Notifications,
                        if (settings.liveUpdatesEnabled) "수업 상태 알림 켜짐" else "수업 상태 알림 꺼짐",
                        "현재 교시 · 종료 시각 · 다음 수업",
                        if (settings.liveUpdatesEnabled) SchoolBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(Modifier.padding(start = 54.dp))
                    SettingsActionRow(
                        Icons.Default.Timer,
                        if (settings.liveUpdatesEnabled) "실시간 알림 끄기" else "실시간 알림 켜기",
                    ) {
                        if (settings.liveUpdatesEnabled) {
                            viewModel.updateLiveUpdates(false)
                        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                            viewModel.updateLiveUpdates(true)
                        } else {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }
            }
            item {
                SettingsSection("학교 도착 자동 실행") {
                    SettingsRow(
                        if (settings.locationMonitoringEnabled) Icons.Default.LocationOn else Icons.Default.LocationOff,
                        if (settings.locationMonitoringEnabled) "학교 도착 감지 켜짐" else "학교 도착 감지 꺼짐",
                        "반경 200m · 경기도 군포시 오금로 118",
                        tint = if (settings.locationMonitoringEnabled) SchoolGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(Modifier.padding(start = 54.dp))
                    SettingsActionRow(
                        Icons.Default.LocationOn,
                        if (settings.locationMonitoringEnabled) "학교 도착 감지 끄기" else "학교 도착 감지 켜기",
                    ) {
                        when {
                            settings.locationMonitoringEnabled -> viewModel.updateLocationMonitoring(false)
                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ->
                                fineLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED ->
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                            else -> viewModel.updateLocationMonitoring(true)
                        }
                    }
                }
            }
            item {
                SettingsSection("학교생활 선택") {
                    SettingsActionRow(Icons.Default.Tune, "과목 · 8교시 · 자습 설정", showChevron = true) { detail = true }
                }
            }
        }
    }
}

@Composable
private fun ProfileSettings(
    settings: UserSettings,
    viewModel: MainViewModel,
    padding: PaddingValues,
    onBack: (() -> Unit)?,
    isInitialSetup: Boolean = false,
    onInitialSetupComplete: (() -> Unit)? = null,
) {
    var name by remember(settings.studentName) { mutableStateOf(settings.studentName) }
    var number by remember(settings.studentNumber) { mutableStateOf(settings.studentNumber) }
    val parsedStudentNumber = parseStudentNumber(number)
    val canSave = name.isNotBlank() && parsedStudentNumber != null

    Column(Modifier.fillMaxSize().padding(padding)) {
        NavigationHeader(
            title = if (isInitialSetup) "학생 정보 입력" else "학생 정보",
            onBack = onBack,
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Text(
                    "저장한 학생 정보는 이 기기에 저장되며 시간표·급식 등 NEIS 정보 조회에 사용됩니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
            }
            item {
                SettingsSection("기본 정보") {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(20) },
                            label = { Text("이름") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = number,
                            onValueChange = { value -> number = value.filter(Char::isDigit).take(5) },
                            label = { Text("학번") },
                            supportingText = {
                                Text(
                                    when {
                                        number.isEmpty() -> "예: 20315 → 2학년 3반 15번"
                                        parsedStudentNumber != null -> "${parsedStudentNumber.grade}학년 ${parsedStudentNumber.classNumber}반 ${parsedStudentNumber.seatNumber}번 · NEIS 정보 자동 설정"
                                        else -> "학번 5자리를 확인해 주세요. 예: 20315"
                                    },
                                )
                            },
                            isError = number.isNotEmpty() && parsedStudentNumber == null,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        viewModel.updateProfile(
                            name,
                            number,
                        )
                        if (isInitialSetup) onInitialSetupComplete?.invoke() else onBack?.invoke()
                    },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(if (isInitialSetup) "시작하기" else "저장")
                }
            }
        }
    }
}

@Composable
private fun ScheduleSettings(settings: UserSettings, viewModel: MainViewModel, padding: PaddingValues, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 18.dp)) {
        item { NavigationHeader("학교생활 선택", onBack = onBack) }
        item {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                SettingsSection("보충 과목") {
                    SupplementaryCourseCatalog.groups.forEachIndexed { index, group ->
                        SupplementaryCourseSetting(group, settings, viewModel)
                        if (index != SupplementaryCourseCatalog.groups.lastIndex) HorizontalDivider()
                    }
                }
                SettingsSection("수요일 8교시") {
                    EighthSetting(
                        "수요일",
                        listOf(3),
                        settings,
                        viewModel,
                        EighthPeriodMode.entries.filter { it != EighthPeriodMode.SUPPLEMENTARY },
                    )
                }
                SettingsSection("요일별 야간자습") {
                    SchoolData.days.forEachIndexed { index, day ->
                        NightStudySetting(day.longName, day.dayOfWeek, settings, viewModel)
                        if (index != SchoolData.days.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SupplementaryCourseSetting(group: SupplementaryCourseGroup, settings: UserSettings, viewModel: MainViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val mode = group.days.firstOrNull()?.let(settings.eighthPeriodByDay::get)
    val selectedCourse = SupplementaryCourseCatalog.selected(group.id, settings)
    val selectedLabel = when {
        !group.usesEighthPeriod -> selectedCourse?.subject ?: "안 함"
        mode == EighthPeriodMode.SELF_STUDY -> "자습"
        mode == EighthPeriodMode.SUPPLEMENTARY -> selectedCourse?.subject ?: "보충 과목 선택"
        else -> "안 함"
    }
    Box {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = true }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(group.title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(selectedLabel, color = SchoolBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                selectedCourse?.let { Text(it.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.widthIn(min = 280.dp)) {
            DropdownMenuItem(
                text = { Text("안 함") },
                onClick = {
                    viewModel.updateSupplementaryCourse(group.id, SupplementaryCourseCatalog.NONE)
                    expanded = false
                },
            )
            if (group.usesEighthPeriod) {
                DropdownMenuItem(
                    text = { Text("자습") },
                    onClick = {
                        viewModel.updateSupplementaryCourse(group.id, SupplementaryCourseCatalog.SELF_STUDY)
                        expanded = false
                    },
                )
            }
            group.options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(option.subject, fontWeight = FontWeight.SemiBold)
                            Text(option.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                    },
                    onClick = {
                        viewModel.updateSupplementaryCourse(group.id, option.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun EighthSetting(title: String, days: List<Int>, settings: UserSettings, viewModel: MainViewModel, modes: List<EighthPeriodMode> = EighthPeriodMode.entries) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            modes.forEach { mode ->
                FilterChip(selected = settings.eighthPeriodByDay[days.first()] == mode, onClick = { viewModel.updateEighthPeriod(days, mode) }, label = { Text(mode.label, fontSize = 12.sp) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NightStudySetting(title: String, day: Int, settings: UserSettings, viewModel: MainViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val value = settings.nightStudyByDay[day] ?: 0
    val labels = listOf("안 함", "1차까지", "2차까지")
    Box {
        Row(Modifier.fillMaxWidth().clickable { expanded = true }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(labels[value], color = SchoolBlue, fontSize = 14.sp)
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.widthIn(min = 150.dp)) {
            labels.forEachIndexed { index, label -> DropdownMenuItem(text = { Text(label) }, onClick = { viewModel.updateNightStudy(day, index); expanded = false }) }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp))
        SchoolCard(contentPadding = PaddingValues(0.dp), content = content)
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String, tint: Color) {
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = tint, modifier = Modifier.width(30.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        }
    }
}

@Composable
private fun SettingsActionRow(icon: ImageVector, title: String, showChevron: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = SchoolBlue, modifier = Modifier.width(30.dp))
        Text(title, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (showChevron) Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(7.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) { Text(action, color = SchoolBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun SchoolCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = schoolBorder(),
        shadowElevation = 3.dp,
    ) { Column(Modifier.padding(contentPadding), content = content) }
}

@Composable
private fun schoolBorder() = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))

@Composable
private fun Badge(text: String, color: Color, compact: Boolean = false) {
    Text(
        text,
        color = color,
        fontSize = if (compact) 10.sp else 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.background(color.copy(alpha = .1f), CircleShape).padding(horizontal = if (compact) 7.dp else 10.dp, vertical = if (compact) 3.dp else 6.dp),
    )
}

private fun timeRange(lesson: Lesson) = "${SchoolTimeline.clock(lesson.startMinute)}–${SchoolTimeline.clock(lesson.endMinute)}"
private fun DayOfWeek.koreanShort() = listOf("월", "화", "수", "목", "금", "토", "일")[value - 1]
