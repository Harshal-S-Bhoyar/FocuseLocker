package com.focuslock.domain.usecase

import com.focuslock.data.local.entity.ScheduleWindowEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

const val MINUTES_PER_DAY = 1440
const val MINUTES_PER_WEEK = 10080

sealed interface ScheduleValidation {
    data object Valid : ScheduleValidation
    data class Overlap(val first: ScheduleWindowEntity, val second: ScheduleWindowEntity) : ScheduleValidation
    data class Invalid(val reason: String) : ScheduleValidation
}

class ScheduleEngine @Inject constructor() {

    fun validate(windows: List<ScheduleWindowEntity>): ScheduleValidation {
        for (w in windows) {
            if (w.weekday !in 1..7) return ScheduleValidation.Invalid("weekday ${w.weekday} outside 1 to 7")
            if (w.startMinutes !in 0..1439 || w.endMinutes !in 0..1440) {
                return ScheduleValidation.Invalid("minutes outside 0 to 1440")
            }
            if (w.startMinutes == w.endMinutes) return ScheduleValidation.Invalid("zero-length window")
        }
        val owner = arrayOfNulls<ScheduleWindowEntity>(MINUTES_PER_WEEK)
        for (w in windows) {
            for (m in absoluteMinutes(w)) {
                val existing = owner[m]
                if (existing != null) return ScheduleValidation.Overlap(existing, w)
                owner[m] = w
            }
        }
        return ScheduleValidation.Valid
    }

    fun dueWindow(
        windows: List<ScheduleWindowEntity>,
        weekdayIso: Int,
        minutesNow: Int
    ): ScheduleWindowEntity? {
        for (w in windows) {
            if (w.startMinutes <= w.endMinutes) {
                if (w.weekday == weekdayIso && minutesNow in w.startMinutes..<w.endMinutes) return w
            } else {
                if (w.weekday == weekdayIso && minutesNow >= w.startMinutes) return w
                val prevDay = if (weekdayIso == 1) 7 else weekdayIso - 1
                if (w.weekday == prevDay && minutesNow < w.endMinutes) return w
            }
        }
        return null
    }

    fun durationMinutes(window: ScheduleWindowEntity): Int {
        return if (window.startMinutes <= window.endMinutes) {
            window.endMinutes - window.startMinutes
        } else {
            MINUTES_PER_DAY - window.startMinutes + window.endMinutes
        }
    }

    fun remainingMinutes(window: ScheduleWindowEntity, weekdayIso: Int, minutesNow: Int): Int {
        return if (window.startMinutes <= window.endMinutes) {
            window.endMinutes - minutesNow
        } else {
            val prevDay = if (weekdayIso == 1) 7 else weekdayIso - 1
            if (window.weekday == prevDay) window.endMinutes - minutesNow
            else MINUTES_PER_DAY - minutesNow + window.endMinutes
        }
    }

    /** Instant of [now] in the device zone, the epoch every remaining/end calculation shares. */
    fun epochMillis(now: LocalDateTime): Long =
        now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /**
     * Epoch of the due window's end boundary, derived from the wall-clock day and not from
     * rounded minutes. Minute rounding gave a window ending at midnight an expiry seconds past
     * it (22:53:46 start + 67 whole minutes = 00:00:46), which blocked the adjacent window.
     * Only meaningful for a window that is currently due.
     */
    fun endEpochMillis(window: ScheduleWindowEntity, now: LocalDateTime): Long {
        val zone = ZoneId.systemDefault()
        // Zone-aware add. Adding real millis to local midnight drifts an hour on DST days,
        // and an early expiry on fall-back is a blocking bypass. plusMinutes follows the offset.
        // Resolve the wall-clock time in the zone; plusMinutes on a ZonedDateTime is
        // instant-based and would re-introduce the one-hour DST drift.
        fun atMinutes(date: LocalDate, minutes: Int): Long =
            date.atTime(minutes / 60, minutes % 60).atZone(zone).toInstant().toEpochMilli()
        val today = now.toLocalDate()
        return if (window.startMinutes <= window.endMinutes) {
            if (window.endMinutes == MINUTES_PER_DAY) atMinutes(today.plusDays(1), 0)
            else atMinutes(today, window.endMinutes)
        } else {
            val prevDay = if (now.dayOfWeek.value == 1) 7 else now.dayOfWeek.value - 1
            if (window.weekday == prevDay) atMinutes(today, window.endMinutes)
            else atMinutes(today.plusDays(1), window.endMinutes)
        }
    }

    /** Exact milliseconds left of a due window. */
    fun remainingMillis(window: ScheduleWindowEntity, now: LocalDateTime): Long =
        endEpochMillis(window, now) - epochMillis(now)

    /**
     * Epoch of the next window start strictly after [now], or null when no window exists.
     * The exact-alarm path uses it so a start is not missed between periodic checks.
     */
    fun nextStartEpochMillis(windows: List<ScheduleWindowEntity>, now: LocalDateTime): Long? {
        if (windows.isEmpty()) return null
        val zone = ZoneId.systemDefault()
        val nowMs = epochMillis(now)
        var best: Long? = null
        for (w in windows) {
            for (dayOffset in 0..7) {
                val date = now.toLocalDate().plusDays(dayOffset.toLong())
                if (date.dayOfWeek.value != w.weekday) continue
                val start = date.atStartOfDay(zone)
                    .plusMinutes(w.startMinutes.toLong())
                    .toInstant()
                    .toEpochMilli()
                if (start > nowMs && (best == null || start < best!!)) best = start
            }
        }
        return best
    }

    /**
     * Occurrence identity of a due window for `schedule_last_fired`. Shared by the arm flow and
     * the periodic worker so both write the same key. Format: `windowId:yyyy-MM-dd`, dated at the
     * window's own start day even when now is on the after-midnight leg.
     */
    fun occurrenceKey(window: ScheduleWindowEntity, weekdayIso: Int, today: LocalDate): String {
        val occurrenceDate = if (window.startMinutes > window.endMinutes) {
            val prevDay = if (weekdayIso == 1) 7 else weekdayIso - 1
            if (window.weekday == prevDay) today.minusDays(1) else today
        } else {
            today
        }
        return "${window.id}:$occurrenceDate"
    }

    private fun absoluteMinutes(w: ScheduleWindowEntity): Iterable<Int> {
        val base = (w.weekday - 1) * MINUTES_PER_DAY
        return if (w.startMinutes <= w.endMinutes) {
            base + w.startMinutes..<base + w.endMinutes
        } else {
            val end = base + MINUTES_PER_DAY + w.endMinutes
            if (end <= MINUTES_PER_WEEK) base + w.startMinutes..<end
            else (base + w.startMinutes..<MINUTES_PER_WEEK) + (0..<end - MINUTES_PER_WEEK)
        }
    }

    private operator fun IntRange.plus(other: IntRange): Iterable<Int> =
        this.asIterable() + other.asIterable()
}
