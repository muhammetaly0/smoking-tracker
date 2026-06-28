package com.example.sigaratakip.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.sigaratakip.data.SigaraRepository
import java.time.LocalDate

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val repo = SigaraRepository(context)
        val today = LocalDate.now()

        when (intent.action) {
            ACTION_RESCHEDULE -> {
                repo.reconcileStreak(today) // seri kırıldıysa notif kapatır
                val state = repo.getState()
                AlarmScheduler.scheduleTodayReminders(
                    context,
                    alreadyCheckedInToday = state.isCheckedInToday(today),
                    notificationsEnabled = state.notificationsEnabled
                )
            }
            else -> {
                val state = repo.getState()
                val shouldNotify = state.notificationsEnabled &&
                        !state.isCheckedInToday(today) &&
                        !state.isStreakBroken(today)
                if (shouldNotify) {
                    NotificationHelper.showReminder(context)
                }
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.example.sigaratakip.ACTION_REMIND"
        const val ACTION_RESCHEDULE = "com.example.sigaratakip.ACTION_RESCHEDULE"
    }
}
