package com.example.sigaratakip.data

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class SigaraState(
    val currentStreak: Int,
    val bestStreak: Int,
    val totalDays: Int,
    val lastPressedDate: LocalDate?,
    val startDate: LocalDate?,
    val notificationsEnabled: Boolean,
    val relapseCount: Int,
    val lastRelapseDate: LocalDate?,
    val calibrationDone: Boolean,
    val daysPerPeriod: Int,
    val packsPerPeriod: Int,
    val pricePerPack: Int
) {
    fun isCheckedInToday(today: LocalDate = LocalDate.now()): Boolean =
        lastPressedDate == today

    fun displayedStreak(today: LocalDate = LocalDate.now()): Int {
        val last = lastPressedDate ?: return 0
        return when {
            last == today -> currentStreak
            last == today.minusDays(1) -> currentStreak
            else -> 0
        }
    }

    fun isStreakBroken(today: LocalDate = LocalDate.now()): Boolean {
        val last = lastPressedDate ?: return false
        return last < today.minusDays(1)
    }

    /**
     * Toplam başarılı gün × günlük paket tüketimi × paket fiyatı.
     * Seri ile alakası yok — tüm zaman boyunca biriken tasarruf.
     */
    fun savedMoney(): Double {
        if (!calibrationDone || daysPerPeriod <= 0) return 0.0
        val dailyPacks = packsPerPeriod.toDouble() / daysPerPeriod.toDouble()
        return totalDays.toDouble() * dailyPacks * pricePerPack.toDouble()
    }
}

class SigaraRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getState(): SigaraState {
        val streak = prefs.getInt(KEY_STREAK, 0)
        val best = prefs.getInt(KEY_BEST_STREAK, 0)
        val total = prefs.getInt(KEY_TOTAL_DAYS, 0)
        val lastStr = prefs.getString(KEY_LAST_PRESSED, null)
        val startStr = prefs.getString(KEY_START_DATE, null)
        val notifEnabled = prefs.getBoolean(KEY_NOTIF_ENABLED, true)
        val relapseCount = prefs.getInt(KEY_RELAPSE_COUNT, 0)
        val lastRelapseStr = prefs.getString(KEY_LAST_RELAPSE, null)
        return SigaraState(
            currentStreak = streak,
            bestStreak = best,
            totalDays = total,
            lastPressedDate = lastStr?.toLocalDateOrNull(),
            startDate = startStr?.toLocalDateOrNull(),
            notificationsEnabled = notifEnabled,
            relapseCount = relapseCount,
            lastRelapseDate = lastRelapseStr?.toLocalDateOrNull(),
            calibrationDone = prefs.getBoolean(KEY_CALIBRATION_DONE, false),
            daysPerPeriod = prefs.getInt(KEY_DAYS_PER_PERIOD, 1),
            packsPerPeriod = prefs.getInt(KEY_PACKS_PER_PERIOD, 1),
            pricePerPack = prefs.getInt(KEY_PRICE_PER_PACK, 110)
        )
    }

    fun saveCalibration(daysPerPeriod: Int, packsPerPeriod: Int, pricePerPack: Int) {
        prefs.edit().apply {
            putInt(KEY_DAYS_PER_PERIOD, daysPerPeriod.coerceAtLeast(1))
            putInt(KEY_PACKS_PER_PERIOD, packsPerPeriod.coerceAtLeast(1))
            putInt(KEY_PRICE_PER_PACK, pricePerPack.coerceAtLeast(1))
            putBoolean(KEY_CALIBRATION_DONE, true)
            apply()
        }
    }

    fun checkInToday(today: LocalDate = LocalDate.now()): CheckInResult {
        val state = getState()
        if (state.lastPressedDate == today) return CheckInResult.AlreadyChecked

        val yesterday = today.minusDays(1)
        val newStreak = when {
            state.lastPressedDate == yesterday -> state.currentStreak + 1
            else -> 1
        }
        val newBest = maxOf(state.bestStreak, newStreak)
        val newTotal = state.totalDays + 1
        val newStart = state.startDate ?: today

        prefs.edit().apply {
            putInt(KEY_STREAK, newStreak)
            putInt(KEY_BEST_STREAK, newBest)
            putInt(KEY_TOTAL_DAYS, newTotal)
            putString(KEY_LAST_PRESSED, today.format(formatter))
            putString(KEY_START_DATE, newStart.format(formatter))
            putBoolean(KEY_NOTIF_ENABLED, true)
            apply()
        }

        return CheckInResult.Success(newStreak)
    }

    fun recordRelapse(today: LocalDate = LocalDate.now()) {
        val state = getState()
        prefs.edit().apply {
            putInt(KEY_STREAK, 0)
            putInt(KEY_RELAPSE_COUNT, state.relapseCount + 1)
            putString(KEY_LAST_RELAPSE, today.format(formatter))
            putBoolean(KEY_NOTIF_ENABLED, false)
            apply()
        }
    }

    fun reconcileStreak(today: LocalDate = LocalDate.now()) {
        val state = getState()
        val last = state.lastPressedDate ?: return
        if (last < today.minusDays(1)) {
            val editor = prefs.edit()
            if (state.currentStreak != 0) editor.putInt(KEY_STREAK, 0)
            if (state.notificationsEnabled) editor.putBoolean(KEY_NOTIF_ENABLED, false)
            editor.apply()
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIF_ENABLED, enabled).apply()
    }

    fun clearAll() = prefs.edit().clear().apply()

    sealed class CheckInResult {
        data class Success(val newStreak: Int) : CheckInResult()
        object AlreadyChecked : CheckInResult()
    }

    companion object {
        private const val PREFS_NAME = "sigara_takip_prefs"
        private const val KEY_STREAK = "streak"
        private const val KEY_BEST_STREAK = "best_streak"
        private const val KEY_TOTAL_DAYS = "total_days"
        private const val KEY_LAST_PRESSED = "last_pressed_date"
        private const val KEY_START_DATE = "start_date"
        private const val KEY_NOTIF_ENABLED = "notifications_enabled"
        private const val KEY_RELAPSE_COUNT = "relapse_count"
        private const val KEY_LAST_RELAPSE = "last_relapse_date"
        private const val KEY_CALIBRATION_DONE = "calibration_done"
        private const val KEY_DAYS_PER_PERIOD = "days_per_period"
        private const val KEY_PACKS_PER_PERIOD = "packs_per_period"
        private const val KEY_PRICE_PER_PACK = "price_per_pack"

        private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        private fun String.toLocalDateOrNull(): LocalDate? = try {
            LocalDate.parse(this, formatter)
        } catch (e: Exception) {
            null
        }
    }
}
