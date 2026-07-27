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
            settings.map { it.grade to it.classNumber }.distinctUntilChanged().collectLatest {
                loadNeis()
            }
        }
    }

    fun refreshNeis() = viewModelScope.launch { loadNeis() }

    fun loadNeisForMonth(date: LocalDate) = viewModelScope.launch { loadNeis(date) }

    fun refreshNotices() = viewModelScope.launch { loadNotices(forceRefresh = true) }

    private suspend fun loadNeis(date: LocalDate = LocalDate.now()) {
        _neisState.value = _neisState.value.copy(isLoading = true, errorMessage = null)
        _neisState.value = neisRepository.load(settings.value, date)
    }

    private suspend fun loadNotices(forceRefresh: Boolean = false) {
        _noticeState.value = _noticeState.value.copy(isLoading = true, errorMessage = null)
        _noticeState.value = noticeRepository.load(forceRefresh)
    }

    fun updateProfile(name: String, number: String, grade: Int, classNumber: Int) = viewModelScope.launch {
        repository.updateProfile(name, number, grade, classNumber)
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
