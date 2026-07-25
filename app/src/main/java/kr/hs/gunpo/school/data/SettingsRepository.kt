package kr.hs.gunpo.school.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "school_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val studentName = stringPreferencesKey("student_name")
        val studentNumber = stringPreferencesKey("student_number")
        val grade = intPreferencesKey("grade")
        val classNumber = intPreferencesKey("class_number")
        val liveUpdates = booleanPreferencesKey("live_updates_enabled")
        val locationMonitoring = booleanPreferencesKey("location_monitoring_enabled")
        fun nightStudy(day: Int) = intPreferencesKey("yaja_$day")
        fun eighthPeriod(day: Int) = stringPreferencesKey("eighth_period_$day")
        fun vacationCourse(period: Int) = stringPreferencesKey("vacation_period_${period}_course")
    }

    val settings: Flow<UserSettings> = context.settingsDataStore.data.map { prefs ->
        UserSettings(
            studentName = prefs[Keys.studentName] ?: "김동우",
            studentNumber = prefs[Keys.studentNumber] ?: "20105",
            grade = prefs[Keys.grade] ?: 2,
            classNumber = prefs[Keys.classNumber] ?: 1,
            nightStudyByDay = (1..5).associateWith { prefs[Keys.nightStudy(it)] ?: 0 },
            eighthPeriodByDay = (1..5).associateWith { day ->
                prefs[Keys.eighthPeriod(day)]?.let { runCatching { EighthPeriodMode.valueOf(it) }.getOrNull() }
                    ?: EighthPeriodMode.EMPTY
            },
            vacationCourseByPeriod = (1..5).associateWith { prefs[Keys.vacationCourse(it)] ?: "self_study" },
            liveUpdatesEnabled = prefs[Keys.liveUpdates] ?: false,
            locationMonitoringEnabled = prefs[Keys.locationMonitoring] ?: false,
        )
    }

    suspend fun updateProfile(name: String, number: String, grade: Int, classNumber: Int) {
        context.settingsDataStore.edit {
            it[Keys.studentName] = name.trim()
            it[Keys.studentNumber] = number.trim()
            it[Keys.grade] = grade.coerceIn(1, 3)
            it[Keys.classNumber] = classNumber.coerceIn(1, 20)
        }
    }

    suspend fun updateNightStudy(day: Int, value: Int) {
        context.settingsDataStore.edit { it[Keys.nightStudy(day)] = value.coerceIn(0, 2) }
    }

    suspend fun updateEighthPeriod(day: Int, mode: EighthPeriodMode) {
        context.settingsDataStore.edit { it[Keys.eighthPeriod(day)] = mode.name }
    }

    suspend fun updateVacationCourse(period: Int, optionID: String) {
        context.settingsDataStore.edit { it[Keys.vacationCourse(period)] = optionID }
    }

    suspend fun updateLiveUpdates(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.liveUpdates] = enabled }
    }

    suspend fun updateLocationMonitoring(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.locationMonitoring] = enabled }
    }
}
