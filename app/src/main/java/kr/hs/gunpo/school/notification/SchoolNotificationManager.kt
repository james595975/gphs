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
import java.time.LocalDateTime
import java.time.ZoneId

object SchoolNotificationManager {
    private const val CHANNEL_ID = "school_live_status"
    private const val NOTIFICATION_ID = 20423
    private const val REQUEST_CODE = 20424
    private const val SYNC_RETRY_MINUTES = 15L

    suspend fun refresh(context: Context) {
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
        val (title, text) = if (schedule.isAvailable) {
            notificationText(SchoolTimeline.moment(now, schedule.lessons))
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
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setRequestPromotedOngoing(true)
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
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
        val minute = now.hour * 60 + now.minute
        val nextMinute = lessons.flatMap { listOf(it.startMinute, it.endMinute) }.firstOrNull { it > minute }
        val next = if (nextMinute != null) {
            now.toLocalDate().atStartOfDay().plusMinutes(nextMinute.toLong()).plusSeconds(2)
        } else {
            now.toLocalDate().plusDays(1).atTime(7, 30)
        }
        scheduleAt(context, next)
    }

    private fun scheduleAt(context: Context, next: LocalDateTime) {
        val millis = next.atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
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
                NotificationChannel(CHANNEL_ID, "현재 수업 상태", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "현재 교시와 다음 수업을 실시간으로 표시합니다."
                    setSound(null, null)
                    enableVibration(false)
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
