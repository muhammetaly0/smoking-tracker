package com.example.sigaratakip.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.sigaratakip.data.SigaraRepository
import java.time.LocalDate

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_PACKAGE_REPLACED
        ) {
            val repo = SigaraRepository(context)
            val today = LocalDate.now()
            repo.reconcileStreak(today)
            val state = repo.getState()
            AlarmScheduler.scheduleTodayReminders(
                context,
                alreadyCheckedInToday = state.isCheckedInToday(today),
                notificationsEnabled = state.notificationsEnabled
            )
        }
    }
}
