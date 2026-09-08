package kr.hs.gunpo.school.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.glance.appwidget.updateAll
import kr.hs.gunpo.school.MainActivity
import kr.hs.gunpo.school.data.Lesson
import kr.hs.gunpo.school.data.NeisRepository
import kr.hs.gunpo.school.data.SettingsRepository
import kr.hs.gunpo.school.domain.SchoolMoment
import kr.hs.gunpo.school.domain.SchoolTimeline
import kr.hs.gunpo.school.domain.NetworkClock
import kr.hs.gunpo.school.widget.SchoolWidget
import kr.hs.gunpo.school.location.SchoolGeofenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import java.time.ZoneId

object SchoolNotificationManager {
    private const val CHANNEL_ID = "school_period_changes_v2"
    private const val NOTIFICATION_ID = 20423
    private const val REQUEST_CODE = 20424
    private const val SYNC_RETRY_MINUTES = 15L
    private val refreshMutex = Mutex()

    suspend fun refresh(context: Context) = refreshMutex.withLock { refreshLocked(context) }

    private suspend fun refreshLocked(context: Context) {
        val settings = SettingsRepository(context).settings.first()
        if (!settings.liveUpdatesEnabled) return stop(context)
        if (!settings.isProfileConfigured) return stop(context)
        createChannel(context)
        NetworkClock.synchronize()
        val now = NetworkClock.now()
        // 알림 리시버에서도 서버 캐시를 조회해 앱을 열지 않아도 해당 학년·반 시간표를 사용한다.
        // 매 교시마다 NEIS를 강제 호출하지 않도록 Cloudflare에는 캐시 우선 모드로 요청한다.
        val repository = NeisRepository(context)
        val neisState = repository.loadCachedServerState(settings, now.toLocalDate())
            ?: repository.load(
                settings = settings,
                today = now.toLocalDate(),
                forceServerSync = false,
            )
        val schedule = NotificationScheduleResolver.resolve(now.toLocalDate(), settings, neisState)
        val moment = SchoolTimeline.moment(now, schedule.lessons)
        val preferences = context.getSharedPreferences("lesson_alerts", Context.MODE_PRIVATE)
        val eventKey = if (schedule.isAvailable) NotificationTransitionPolicy.alertKey(now, moment)?.let {
            "${settings.grade}:${settings.classNumber}:$it"
        } else null
        val shouldAlert = eventKey != null && eventKey != preferences.getString("last_alert", null)
        val (title, text) = if (schedule.isAvailable) {
            notificationText(moment)
        } else {
            "시간표 동기화 대기 중" to "Cloudflare 연결을 확인한 뒤 자동으로 다시 시도합니다."
        }
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(!shouldAlert)
            .setSilent(!shouldAlert)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setRequestPromotedOngoing(true)
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
            if (shouldAlert) preferences.edit().putString("last_alert", eventKey).apply()
        }
        SchoolWidget().updateAll(context)
        if (schedule.isAvailable) {
            scheduleNext(context, now, schedule.lessons)
        } else {
            scheduleAt(context, now.plusMinutes(SYNC_RETRY_MINUTES))
        }
    }

    fun stop(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.cancel(pendingReceiver(context))
    }

    private fun notificationText(moment: SchoolMoment): Pair<String, String> = when (moment) {
        is SchoolMoment.LunchBreak -> "점심시간" to
            "12:10–13:10${moment.next?.let { " · 다음 ${it.period}교시 ${it.subject} ${SchoolTimeline.clock(it.startMinute)} 시작" } ?: " · 오후 수업 없음"}"
        is SchoolMoment.InClass -> "${moment.lesson.period}교시 ${moment.lesson.subject}" to
            "${SchoolTimeline.clock(moment.lesson.endMinute)} 종료${moment.next?.let { " · 다음 ${it.period}교시 ${it.subject}" } ?: ""}"
        is SchoolMoment.BetweenClasses -> "쉬는 시간" to
            "${SchoolTimeline.clock(moment.next.startMinute)} ${moment.next.period}교시 ${moment.next.subject} 시작"
        is SchoolMoment.BeforeSchool -> "수업 시작 전" to
            "${SchoolTimeline.clock(moment.next.startMinute)} ${moment.next.subject} 시작"
        is SchoolMoment.Finished -> "오늘 수업 종료" to "마지막 수업은 ${moment.last.subject}이었어요."
        SchoolMoment.NoSchool -> "오늘은 수업이 없어요" to "군포고 학교생활 일정을 확인하세요."
    }

    private fun scheduleNext(context: Context, now: LocalDateTime, lessons: List<Lesson>) {
        scheduleAt(context, NotificationTransitionPolicy.nextBoundary(now, lessons))
    }

    private fun scheduleAt(context: Context, next: LocalDateTime) {
        val millis = System.currentTimeMillis() + java.time.Duration.between(NetworkClock.now(), next).toMillis().coerceAtLeast(0)
        val alarm = context.getSystemService(AlarmManager::class.java)
        if (alarm.canScheduleExactAlarms()) {
            try {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingReceiver(context))
                return
            } catch (_: SecurityException) { /* Permission may have been revoked since the check. */ }
        }
        alarm.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            millis,
            pendingReceiver(context),
        )
    }

    private fun pendingReceiver(context: Context) = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, SchoolNotificationReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "교시 변경·점심시간", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "매 교시 시작과 점심시간에 알리고 현재 수업 상태를 표시합니다."
                    enableVibration(true)
                },
            )
        }
    }
}

class SchoolNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { SchoolNotificationManager.refresh(context) } finally { pending.finish() }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = SettingsRepository(context).settings.first()
                if (settings.locationMonitoringEnabled) SchoolGeofenceManager.enable(context)
                SchoolNotificationManager.refresh(context)
            } finally { pending.finish() }
        }
    }
}
