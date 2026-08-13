package kr.hs.gunpo.school

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kr.hs.gunpo.school.data.EighthPeriodMode
import kr.hs.gunpo.school.data.SettingsRepository
import kr.hs.gunpo.school.data.UserSettings
import kr.hs.gunpo.school.data.NeisRepository
import kr.hs.gunpo.school.data.NeisState
import kr.hs.gunpo.school.data.NoticeState
import kr.hs.gunpo.school.data.SchoolNoticeRepository
import kr.hs.gunpo.school.notification.SchoolNotificationManager
import kr.hs.gunpo.school.location.SchoolGeofenceManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    private val neisRepository = NeisRepository(application)
    private val noticeRepository = SchoolNoticeRepository(application)

    val settings = repository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserSettings(),
    )

    private val _neisState = MutableStateFlow(NeisState(isLoading = true))
    val neisState: StateFlow<NeisState> = _neisState.asStateFlow()

    private val _noticeState = MutableStateFlow(NoticeState(isLoading = true))
    val noticeState: StateFlow<NoticeState> = _noticeState.asStateFlow()

    init {
        viewModelScope.launch { loadNotices() }
        viewModelScope.launch {
            settings
                .filter { it.isLoaded && it.isProfileConfigured }
                .map { current -> Triple(current.grade, current.classNumber, current) }
                .distinctUntilChanged { old, new -> old.first == new.first && old.second == new.second }
                .collectLatest { (_, _, current) ->
                    loadNeis(current)
                }
        }
    }

    fun refreshNeis() = viewModelScope.launch {
        settings.value.takeIf { it.isLoaded && it.isProfileConfigured }?.let { loadNeis(it) }
    }

    fun loadNeisForMonth(date: LocalDate) = viewModelScope.launch {
        settings.value.takeIf { it.isLoaded && it.isProfileConfigured }?.let { loadNeis(it, date) }
    }

    fun refreshNotices() = viewModelScope.launch { loadNotices(forceRefresh = true) }

    private suspend fun loadNeis(userSettings: UserSettings, date: LocalDate = LocalDate.now()) {
        val previous = _neisState.value
        _neisState.value = if (
            previous.grade == userSettings.grade && previous.classNumber == userSettings.classNumber
        ) {
            previous.copy(isLoading = true, errorMessage = null)
        } else {
            // 다른 반으로 전환할 때 이전 반 시간표가 새 반의 시간표처럼 보이지 않게 한다.
            NeisState(
                isLoading = true,
                grade = userSettings.grade,
                classNumber = userSettings.classNumber,
            )
        }
        val result = neisRepository.load(userSettings, date)
        val latestSettings = settings.value
        if (latestSettings.grade == userSettings.grade && latestSettings.classNumber == userSettings.classNumber) {
            _neisState.value = result
        }
    }

    private suspend fun loadNotices(forceRefresh: Boolean = false) {
        _noticeState.value = _noticeState.value.copy(isLoading = true, errorMessage = null)
        _noticeState.value = noticeRepository.load(forceRefresh)
    }

    fun updateProfile(name: String, number: String) = viewModelScope.launch {
        repository.updateProfile(name, number)
    }

    fun updateNightStudy(day: Int, value: Int) = viewModelScope.launch {
        repository.updateNightStudy(day, value)
    }

    fun updateEighthPeriod(day: Int, mode: EighthPeriodMode) = viewModelScope.launch {
        repository.updateEighthPeriod(day, mode)
    }

    fun updateEighthPeriod(days: List<Int>, mode: EighthPeriodMode) = viewModelScope.launch {
        days.forEach { repository.updateEighthPeriod(it, mode) }
    }

    fun updateVacationCourse(period: Int, optionID: String) = viewModelScope.launch {
        repository.updateVacationCourse(period, optionID)
    }

    fun updateLiveUpdates(enabled: Boolean) = viewModelScope.launch {
        repository.updateLiveUpdates(enabled)
        if (enabled) SchoolNotificationManager.refresh(getApplication())
        else SchoolNotificationManager.stop(getApplication())
    }

    fun updateLocationMonitoring(enabled: Boolean) = viewModelScope.launch {
        if (enabled) {
            val registered = SchoolGeofenceManager.enable(getApplication())
            repository.updateLocationMonitoring(registered)
        } else {
            SchoolGeofenceManager.disable(getApplication())
            repository.updateLocationMonitoring(false)
        }
    }

}
