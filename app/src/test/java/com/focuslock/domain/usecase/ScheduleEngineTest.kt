package com.focuslock.domain.usecase

import com.focuslock.data.local.entity.ScheduleWindowEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlin.random.Random

class ScheduleEngineTest {

    private val engine = ScheduleEngine()

    private fun window(weekday: Int, start: Int, end: Int) =
        ScheduleWindowEntity(weekday = weekday, startMinutes = start, endMinutes = end)

    @Test
    fun `overnight monday run collides with tuesday morning window`() {
        val monday = window(1, 1200, 840)
        val tuesday = window(2, 600, 720)

        val result = engine.validate(listOf(monday, tuesday)) as ScheduleValidation.Overlap
        assertEquals(monday, result.first)
        assertEquals(tuesday, result.second)
    }

    @Test
    fun `adjacent windows touching at boundary are allowed`() {
        val result = engine.validate(listOf(window(2, 600, 840), window(2, 840, 1200)))
        assertEquals(ScheduleValidation.Valid, result)
    }

    @Test
    fun `sunday overnight wrap still detects monday morning collision`() {
        val result = engine.validate(listOf(window(7, 1200, 600), window(1, 300, 400)))
        assertTrue(result is ScheduleValidation.Overlap)
    }

    @Test
    fun `invalid rows rejected with reason`() {
        assertTrue(engine.validate(listOf(window(0, 0, 60))) is ScheduleValidation.Invalid)
        assertTrue(engine.validate(listOf(window(8, 0, 60))) is ScheduleValidation.Invalid)
        assertTrue(engine.validate(listOf(window(1, 0, 1441))) is ScheduleValidation.Invalid)
        assertTrue(engine.validate(listOf(window(1, 500, 500))) is ScheduleValidation.Invalid)
    }

    @Test
    fun `due window honors start-day ownership across midnight`() {
        val monday = window(1, 1200, 840)

        assertEquals(monday, engine.dueWindow(listOf(monday), 1, 1300))
        assertEquals(monday, engine.dueWindow(listOf(monday), 2, 500))
        assertNull(engine.dueWindow(listOf(monday), 2, 900))
        assertNull(engine.dueWindow(listOf(monday), 1, 900))
    }

    @Test
    fun `due window start inclusive end exclusive`() {
        val w = window(3, 480, 600)
        assertEquals(w, engine.dueWindow(listOf(w), 3, 480))
        assertNull(engine.dueWindow(listOf(w), 3, 600))
    }

    @Test
    fun `durations cover same-day overnight and full-day preset`() {
        assertEquals(120, engine.durationMinutes(window(3, 480, 600)))
        assertEquals(1080, engine.durationMinutes(window(1, 1200, 840)))
        assertEquals(1439, engine.durationMinutes(window(6, 0, 1439)))
    }

    @Test
    fun `remaining minutes reach the same window end`() {
        val monday = window(1, 1200, 840)
        assertEquals(1080 - 100, engine.remainingMinutes(monday, 1, 1300))
        assertEquals(840 - 500, engine.remainingMinutes(monday, 2, 500))
        assertEquals(120 - 20, engine.remainingMinutes(window(3, 480, 600), 3, 500))
    }

    @Test
    fun `fuzz covered minutes equal summed durations when valid`() {
        val random = Random(42)
        repeat(200) {
            val windows = List(random.nextInt(1, 6)) {
                val start = random.nextInt(0, 1440)
                var end = random.nextInt(0, 1440)
                if (end == start) end = (end + 1) % 1440
                window(random.nextInt(1, 8), start, end)
            }
            val covered = engineCoveredCount(windows)
            when (val result = engine.validate(windows)) {
                is ScheduleValidation.Valid ->
                    assertEquals(windows.sumOf { engine.durationMinutes(it) }, covered)
                is ScheduleValidation.Overlap ->
                    assertTrue(covered < windows.sumOf { engine.durationMinutes(it) })
                is ScheduleValidation.Invalid -> throw AssertionError("generated rows are always in range")
            }
        }
    }

    @Test
    fun `validate accepts end at midnight boundary`() {
        assertEquals(ScheduleValidation.Valid, engine.validate(listOf(window(1, 0, 1440))))
        assertTrue(engine.validate(listOf(window(1, 0, 1441))) is ScheduleValidation.Invalid)
    }

    @Test
    fun `full-day window duration is 1440 minutes`() {
        assertEquals(1440, engine.durationMinutes(window(2, 0, 1440)))
    }

    @Test
    fun `full-day window overlaps any same-day window`() {
        val result = engine.validate(listOf(window(2, 0, 1440), window(2, 600, 720)))
        assertTrue(result is ScheduleValidation.Overlap)
    }

    @Test
    fun `full-day window due at 2359 with one minute remaining`() {
        val fullDay = window(4, 0, 1440)
        assertEquals(fullDay, engine.dueWindow(listOf(fullDay), 4, 1439))
        assertEquals(1, engine.remainingMinutes(fullDay, 4, 1439))
    }

    @Test
    fun `remaining millis end exactly at the window boundary`() {
        // The reported bug: 22:53:46 into a Saturday window ending at midnight was granted 67
        // whole minutes (00:00:46), 46 seconds past the boundary. Exact millis end at 00:00:00.
        val saturday = window(6, 781, 1440)
        val now = LocalDateTime.of(2026, 10, 17, 22, 53, 46)

        assertEquals(66 * 60L * 1000L + 14 * 1000L, engine.remainingMillis(saturday, now))
    }

    @Test
    fun `remaining millis cover both legs of an overnight window`() {
        val monday = window(1, 1200, 840)

        // Monday 21:00 to Tuesday 14:00 is 17 hours.
        assertEquals(
            17L * 60L * 60L * 1000L,
            engine.remainingMillis(monday, LocalDateTime.of(2026, 10, 12, 21, 0))
        )
        // Tuesday 08:20:30 to Tuesday 14:00 is 5 h 39 min 30 s.
        assertEquals(
            5L * 60L * 60L * 1000L + 39L * 60L * 1000L + 30_000L,
            engine.remainingMillis(monday, LocalDateTime.of(2026, 10, 13, 8, 20, 30))
        )
    }

    @Test
    fun `full-day window ends at the next midnight to the millisecond`() {
        val fullDay = window(6, 0, 1440)

        assertEquals(46_000L, engine.remainingMillis(fullDay, LocalDateTime.of(2026, 10, 17, 23, 59, 14)))
    }

    @Test
    fun `spring-forward window ends at its wall-clock boundary not 60 minutes late`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val zone = ZoneId.of("America/New_York")
            // Sunday 2026-03-08: 02:00 EST jumps to 03:00 EDT, so 01:00-05:00 is 3 real hours.
            val sunday = window(7, 60, 300)
            val now = LocalDateTime.of(2026, 3, 8, 1, 30)

            assertEquals(
                ZonedDateTime.of(2026, 3, 8, 5, 0, 0, 0, zone).toInstant().toEpochMilli(),
                engine.endEpochMillis(sunday, now)
            )
            assertEquals(
                2L * 60L * 60L * 1000L + 30L * 60L * 1000L,
                engine.remainingMillis(sunday, now)
            )
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `fall-back window does not expire an hour early`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val zone = ZoneId.of("America/New_York")
            // Sunday 2026-11-01: 02:00 EDT falls back to 01:00 EST, so 00:00-05:00 is 6 real hours.
            val sunday = window(7, 0, 300)
            val now = LocalDateTime.of(2026, 11, 1, 0, 30)

            assertEquals(
                ZonedDateTime.of(2026, 11, 1, 5, 0, 0, 0, zone).toInstant().toEpochMilli(),
                engine.endEpochMillis(sunday, now)
            )
            // 00:30 EDT to 05:00 EST crosses the repeated hour: 5 h 30 min of real time.
            assertEquals(
                5L * 60L * 60L * 1000L + 30L * 60L * 1000L,
                engine.remainingMillis(sunday, now)
            )
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `occurrence key dates overnight windows at their start day`() {
        val monday = ScheduleWindowEntity(id = 7L, weekday = 1, startMinutes = 1200, endMinutes = 840)

        assertEquals("7:2026-10-12", engine.occurrenceKey(monday, 1, LocalDate.of(2026, 10, 12)))
        assertEquals("7:2026-10-12", engine.occurrenceKey(monday, 2, LocalDate.of(2026, 10, 13)))
    }

    @Test
    fun `occurrence key for same-day windows uses the current date`() {
        val saturday = ScheduleWindowEntity(id = 8L, weekday = 6, startMinutes = 60, endMinutes = 120)

        assertEquals("8:2026-10-17", engine.occurrenceKey(saturday, 6, LocalDate.of(2026, 10, 17)))
    }

    private fun engineCoveredCount(windows: List<ScheduleWindowEntity>): Int {
        val seen = BooleanArray(MINUTES_PER_WEEK)
        var count = 0
        for (w in windows) {
            val base = (w.weekday - 1) * MINUTES_PER_DAY
            val spans = if (w.startMinutes <= w.endMinutes) {
                listOf(base + w.startMinutes..<base + w.endMinutes)
            } else {
                val end = base + MINUTES_PER_DAY + w.endMinutes
                if (end <= MINUTES_PER_WEEK) listOf(base + w.startMinutes..<end)
                else listOf(base + w.startMinutes..<MINUTES_PER_WEEK, 0..<end - MINUTES_PER_WEEK)
            }
            for (span in spans) for (m in span) if (!seen[m]) {
                seen[m] = true
                count++
            }
        }
        return count
    }
}
