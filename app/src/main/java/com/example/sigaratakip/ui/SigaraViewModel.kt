package com.example.sigaratakip.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sigaratakip.data.SigaraRepository
import com.example.sigaratakip.data.SigaraState
import com.example.sigaratakip.notification.AlarmScheduler
import com.example.sigaratakip.notification.NotificationHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class SigaraViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SigaraRepository(application)

    private val _state = MutableStateFlow(repo.getState())
    val state: StateFlow<SigaraState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private val _showCalibration = MutableStateFlow(false)
    val showCalibration: StateFlow<Boolean> = _showCalibration.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        repo.reconcileStreak(LocalDate.now())
        val current = repo.getState()
        _state.value = current
        AlarmScheduler.scheduleTodayReminders(
            getApplication(),
            alreadyCheckedInToday = current.isCheckedInToday(),
            notificationsEnabled = current.notificationsEnabled
        )
    }

    fun onCheckInClicked() {
        val now = LocalTime.now()
        if (now.isBefore(EARLIEST_CHECK_IN)) {
            viewModelScope.launch { _messages.emit(UiMessage.TooEarly) }
            return
        }

        // İlk işaretlemede önce kalibrasyon iste
        if (!_state.value.calibrationDone) {
            _showCalibration.value = true
            return
        }

        performCheckIn()
    }

    fun onCalibrationConfirmed(daysPerPeriod: Int, packsPerPeriod: Int, pricePerPack: Int) {
        repo.saveCalibration(daysPerPeriod, packsPerPeriod, pricePerPack)
        _showCalibration.value = false
        _state.value = repo.getState()
        performCheckIn()
    }

    fun onCalibrationDismissed() {
        _showCalibration.value = false
    }

    private fun performCheckIn() {
        val result = repo.checkInToday(LocalDate.now())
        val newState = repo.getState()
        _state.value = newState

        when (result) {
            is SigaraRepository.CheckInResult.Success -> {
                AlarmScheduler.scheduleTodayReminders(
                    getApplication(),
                    alreadyCheckedInToday = true,
                    notificationsEnabled = true
                )
                NotificationHelper.cancelReminder(getApplication())
                viewModelScope.launch { _messages.emit(UiMessage.CheckInSuccess) }
            }
            SigaraRepository.CheckInResult.AlreadyChecked -> {
                viewModelScope.launch { _messages.emit(UiMessage.AlreadyChecked) }
            }
        }
    }

    fun onRelapseConfirmed() {
        repo.recordRelapse(LocalDate.now())
        val newState = repo.getState()
        _state.value = newState
        AlarmScheduler.scheduleTodayReminders(
            getApplication(),
            alreadyCheckedInToday = false,
            notificationsEnabled = false
        )
        NotificationHelper.cancelReminder(getApplication())
        viewModelScope.launch { _messages.emit(UiMessage.RelapseRecorded) }
    }

    sealed class UiMessage {
        object CheckInSuccess : UiMessage()
        object AlreadyChecked : UiMessage()
        object TooEarly : UiMessage()
        object RelapseRecorded : UiMessage()
    }

    companion object {
        val EARLIEST_CHECK_IN: LocalTime = LocalTime.of(19, 0)
    }
}
