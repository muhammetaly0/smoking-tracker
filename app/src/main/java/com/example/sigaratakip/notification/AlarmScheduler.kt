package com.example.sigaratakip.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Günlük hatırlatma alarmlarını planlar.
 *
 * Zamanlama:
 *  - 19:00, 20:00, 21:00, 22:00 (her saat başı)
 *  - 22:30, 23:00, 23:30 (her yarım saat)
 *
 * Toplam 7 alarm. Her biri için benzersiz requestCode kullanırız.
 * Geçmiş zamanlı alarmları planlamayız (uygulamayı 21:15'te açtıysa 19/20/21:00 atlanır).
 */
object AlarmScheduler {

    private val ALARM_TIMES: List<LocalTime> = listOf(
        LocalTime.of(19, 0),
        LocalTime.of(20, 0),
        LocalTime.of(21, 0),
        LocalTime.of(22, 0),
        LocalTime.of(22, 30),
        LocalTime.of(23, 0),
        LocalTime.of(23, 30)
    )

    private const val REQUEST_CODE_BASE = 5000

    /**
     * Bugünün kalan tüm bildirim alarmlarını planlar.
     * Bugün basılı veya bildirim devre dışıysa sadece gece yarısı re-schedule alarmını kurar.
     */
    fun scheduleTodayReminders(
        context: Context,
        alreadyCheckedInToday: Boolean,
        notificationsEnabled: Boolean = true
    ) {
        cancelAll(context)
        // Gece yarısı re-schedule her zaman kurulmalı (gün dönümünde durumu yeniden değerlendirsin)
        scheduleMidnightRescheduler(context)
        if (alreadyCheckedInToday || !notificationsEnabled) return

        val now = LocalDateTime.now()
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()

        ALARM_TIMES.forEachIndexed { index, time ->
            val triggerDateTime = LocalDateTime.of(today, time)
            if (triggerDateTime.isAfter(now)) {
                val triggerMillis = triggerDateTime.atZone(zone).toInstant().toEpochMilli()
                scheduleExact(context, triggerMillis, REQUEST_CODE_BASE + index)
            }
        }
    }

    private fun scheduleMidnightRescheduler(context: Context) {
        val zone = ZoneId.systemDefault()
        val midnight = LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(0, 1))
        val midnightMillis = midnight.atZone(zone).toInstant().toEpochMilli()
        scheduleExact(context, midnightMillis, REQUEST_CODE_BASE + 100, isRescheduler = true)
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        ALARM_TIMES.indices.forEach { index ->
            val pi = buildPendingIntent(context, REQUEST_CODE_BASE + index, isRescheduler = false, flagsOnly = true)
            pi?.let { alarmManager.cancel(it) }
        }
        val midnightPi = buildPendingIntent(context, REQUEST_CODE_BASE + 100, isRescheduler = true, flagsOnly = true)
        midnightPi?.let { alarmManager.cancel(it) }
    }

    private fun scheduleExact(
        context: Context,
        triggerAtMillis: Long,
        requestCode: Int,
        isRescheduler: Boolean = false
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = buildPendingIntent(context, requestCode, isRescheduler, flagsOnly = false) ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pi
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pi
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pi
                )
            }
        } catch (se: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pi
            )
        }
    }

    private fun buildPendingIntent(
        context: Context,
        requestCode: Int,
        isRescheduler: Boolean,
        flagsOnly: Boolean
    ): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = if (isRescheduler) ReminderReceiver.ACTION_RESCHEDULE
            else ReminderReceiver.ACTION_REMIND
        }
        val flags = if (flagsOnly) {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }
}
