package kr.hs.gunpo.school.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kr.hs.gunpo.school.MainActivity
import kr.hs.gunpo.school.data.SchoolData
import kr.hs.gunpo.school.data.SettingsRepository
import kr.hs.gunpo.school.domain.SchoolMoment
import kr.hs.gunpo.school.domain.SchoolTimeline
import kr.hs.gunpo.school.domain.NetworkClock
import kotlinx.coroutines.flow.first

class SchoolWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = SettingsRepository(context).settings.first()
        NetworkClock.synchronize()
        val now = NetworkClock.now()
        val moment = SchoolTimeline.moment(now, SchoolData.lessonsFor(now.toLocalDate(), settings))
        provideContent { WidgetContent(settings.className, moment) }
    }
}

@Composable
private fun WidgetContent(className: String, moment: SchoolMoment) {
    val (eyebrow, title, detail) = when (moment) {
        is SchoolMoment.InClass -> Triple("현재 ${moment.lesson.period}교시", moment.lesson.subject, "${SchoolTimeline.clock(moment.lesson.endMinute)} 종료${moment.next?.let { " · 다음 ${it.subject}" } ?: ""}")
        is SchoolMoment.BetweenClasses -> Triple("쉬는 시간", "다음 ${moment.next.subject}", "${SchoolTimeline.clock(moment.next.startMinute)} 시작 · ${moment.next.room}")
        is SchoolMoment.LunchBreak -> Triple("점심시간", "12:10–13:10", moment.next?.let { "다음 ${it.period}교시 ${it.subject}" } ?: "오후 수업 없음")
        is SchoolMoment.BeforeSchool -> Triple("수업 시작 전", moment.next.subject, "${SchoolTimeline.clock(moment.next.startMinute)} 시작")
        is SchoolMoment.Finished -> Triple("수업 종료", "오늘도 수고했어요", "마지막 ${moment.last.subject}")
        SchoolMoment.NoSchool -> Triple("군포고 학교생활", "오늘 수업 없음", "학사 일정을 확인하세요")
    }
    Column(GlanceModifier.fillMaxSize().background(ColorProvider(Color(0xFF0B2B59))).clickable(actionStartActivity<MainActivity>()).padding(16.dp)) {
        Row(GlanceModifier.fillMaxWidth()) {
            Text(eyebrow, style = TextStyle(color = ColorProvider(Color(0xFFB8D7FF)), fontSize = 12.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.width(8.dp))
            Text(className, style = TextStyle(color = ColorProvider(Color.White), fontSize = 12.sp))
        }
        Spacer(GlanceModifier.height(8.dp))
        Text(title, style = TextStyle(color = ColorProvider(Color.White), fontSize = 22.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.height(4.dp))
        Text(detail, style = TextStyle(color = ColorProvider(Color(0xFFD7E8FF)), fontSize = 12.sp))
    }
}

class SchoolWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SchoolWidget()
}
