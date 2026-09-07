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
        val smsVerified = booleanPreferencesKey("sms_verified")
        val accountId = stringPreferencesKey("account_id")
        val accountEmail = stringPreferencesKey("account_email")
        val accountUid = stringPreferencesKey("account_uid")
        val grade = intPreferencesKey("grade")
        val classNumber = intPreferencesKey("class_number")
        val liveUpdates = booleanPreferencesKey("live_updates_enabled")
        val locationMonitoring = booleanPreferencesKey("location_monitoring_enabled")
        fun nightStudy(day: Int) = intPreferencesKey("yaja_$day")
        fun eighthPeriod(day: Int) = stringPreferencesKey("eighth_period_$day")
        fun vacationCourse(period: Int) = stringPreferencesKey("vacation_period_${period}_course")
        fun supplementaryCourse(groupId: String) = stringPreferencesKey("supplementary_course_$groupId")
    }

    val settings: Flow<UserSettings> = context.settingsDataStore.data.map { prefs ->
        val studentNumber = prefs[Keys.studentNumber].orEmpty()
        val parsedStudentNumber = parseStudentNumber(studentNumber)
        UserSettings(
            studentName = prefs[Keys.studentName].orEmpty(),
            studentNumber = studentNumber,
            isSmsVerified = prefs[Keys.smsVerified] ?: false,
            accountId = prefs[Keys.accountId].orEmpty(),
            accountEmail = prefs[Keys.accountEmail].orEmpty(),
            accountUid = prefs[Keys.accountUid].orEmpty(),
            // 학년과 반은 학번에서 다시 계산해 과거에 잘못 저장된 설정도 자동 복구한다.
            grade = parsedStudentNumber?.grade ?: prefs[Keys.grade] ?: 2,
            classNumber = parsedStudentNumber?.classNumber ?: prefs[Keys.classNumber] ?: 1,
            nightStudyByDay = (1..5).associateWith { prefs[Keys.nightStudy(it)] ?: 0 },
            eighthPeriodByDay = (1..5).associateWith { day ->
                prefs[Keys.eighthPeriod(day)]?.let { runCatching { EighthPeriodMode.valueOf(it) }.getOrNull() }
                    ?: EighthPeriodMode.EMPTY
            },
            vacationCourseByPeriod = (1..5).associateWith { prefs[Keys.vacationCourse(it)] ?: "self_study" },
            supplementaryCourseByGroup = SupplementaryCourseCatalog.groups.associate { group ->
                group.id to (prefs[Keys.supplementaryCourse(group.id)] ?: SupplementaryCourseCatalog.NONE)
            },
            liveUpdatesEnabled = prefs[Keys.liveUpdates] ?: false,
            locationMonitoringEnabled = prefs[Keys.locationMonitoring] ?: false,
            isLoaded = true,
        )
    }

    suspend fun updateProfile(name: String, number: String) {
        val studentNumber = requireNotNull(parseStudentNumber(number)) { "올바른 5자리 학번이 필요합니다." }
        context.settingsDataStore.edit {
            it[Keys.studentName] = name.trim()
            it[Keys.studentNumber] = studentNumber.value
            it[Keys.smsVerified] = true
            it[Keys.grade] = studentNumber.grade
            it[Keys.classNumber] = studentNumber.classNumber
        }
    }

    suspend fun updateAccount(loginId: String, email: String, firebaseUid: String) {
        context.settingsDataStore.edit {
            it[Keys.accountId] = loginId.trim().lowercase()
            it[Keys.accountEmail] = email.trim().lowercase()
            it[Keys.accountUid] = firebaseUid
        }
    }

    suspend fun clearPrivateData() {
        context.settingsDataStore.edit {
            it.remove(Keys.studentName)
            it.remove(Keys.studentNumber)
            it.remove(Keys.smsVerified)
            it.remove(Keys.accountId)
            it.remove(Keys.accountEmail)
            it.remove(Keys.accountUid)
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

    suspend fun updateSupplementaryCourse(groupId: String, selectionId: String) {
        val group = requireNotNull(SupplementaryCourseCatalog.group(groupId)) { "알 수 없는 보충수업 그룹입니다." }
        val validSelections = group.options.map { it.id }.toSet() +
            if (group.usesEighthPeriod) setOf(SupplementaryCourseCatalog.NONE, SupplementaryCourseCatalog.SELF_STUDY)
            else setOf(SupplementaryCourseCatalog.NONE)
        require(selectionId in validSelections) { "알 수 없는 보충 과목입니다." }
        context.settingsDataStore.edit { prefs ->
            val selectedCourseId = selectionId.takeUnless {
                it == SupplementaryCourseCatalog.NONE || it == SupplementaryCourseCatalog.SELF_STUDY
            }
            if (selectedCourseId == null) prefs.remove(Keys.supplementaryCourse(groupId))
            else prefs[Keys.supplementaryCourse(groupId)] = selectedCourseId

            if (group.usesEighthPeriod) {
                val mode = when (selectionId) {
                    SupplementaryCourseCatalog.NONE -> EighthPeriodMode.EMPTY
                    SupplementaryCourseCatalog.SELF_STUDY -> EighthPeriodMode.SELF_STUDY
                    else -> EighthPeriodMode.SUPPLEMENTARY
                }
                group.days.forEach { day -> prefs[Keys.eighthPeriod(day)] = mode.name }
            }
        }
    }

    suspend fun updateLiveUpdates(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.liveUpdates] = enabled }
    }

    suspend fun updateLocationMonitoring(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.locationMonitoring] = enabled }
    }

}
