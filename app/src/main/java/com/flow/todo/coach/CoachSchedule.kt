package com.flow.todo.coach

import java.time.LocalDateTime
import kotlin.math.roundToLong
import kotlin.random.Random

/** Pure scheduling / gating rules shared by the worker, the foreground triggers, and tests. */
object CoachSchedule {

    fun isQuietHour(hour: Int, settings: CoachSettings): Boolean {
        val start = settings.quietStartHour
        val end = settings.quietEndHour
        return when {
            start == end -> false
            start < end -> hour in start until end
            else -> hour >= start || hour < end // wraps midnight, e.g. 22 -> 8
        }
    }

    /** Minutes in the daily window during which automatic messages may appear. */
    fun activeMinutesPerDay(settings: CoachSettings): Int {
        val start = settings.quietStartHour
        val end = settings.quietEndHour
        val quietHours = when {
            start == end -> 0
            start < end -> end - start
            else -> (24 - start) + end
        }
        return ((24 - quietHours) * 60).coerceAtLeast(60)
    }

    /**
     * Delay until the next RANDOM message. Mean gap = active window / randomPerDay, drawn uniformly
     * from 0.5x..1.5x so times feel unpredictable. If the draw lands in quiet hours it is pushed to
     * the end of the quiet window (plus a random offset).
     */
    fun nextRandomDelayMinutes(now: LocalDateTime, settings: CoachSettings, random: Random = Random.Default): Long {
        val perDay = settings.randomPerDay.coerceIn(1, 24)
        val meanGap = activeMinutesPerDay(settings).toDouble() / perDay
        var delay = (meanGap * (0.5 + random.nextDouble())).roundToLong().coerceAtLeast(15L)

        var target = now.plusMinutes(delay)
        var guard = 0
        while (isQuietHour(target.hour, settings) && guard++ < 48) {
            // Jump to the top of the next hour and re-check.
            target = target.withMinute(0).withSecond(0).withNano(0).plusHours(1)
        }
        if (guard > 0) {
            target = target.plusMinutes((random.nextDouble() * meanGap * 0.5).roundToLong())
            delay = java.time.Duration.between(now, target).toMinutes().coerceAtLeast(15L)
        }
        return delay
    }

    sealed class Gate {
        object Open : Gate()
        data class Blocked(val reason: String) : Gate()
    }

    /**
     * Whether an *automatic* message may be generated right now. Manual taps bypass quiet hours,
     * the min gap and the daily cap (but not the hard ceiling checked by the engine).
     */
    fun gateAutomatic(
        now: LocalDateTime,
        settings: CoachSettings,
        lastGeneratedMillis: Long,
        nowMillis: Long,
        generatedToday: Int
    ): Gate {
        if (isQuietHour(now.hour, settings)) return Gate.Blocked("quiet hours")
        if (generatedToday >= settings.dailyCap) return Gate.Blocked("daily cap reached")
        val sinceMin = (nowMillis - lastGeneratedMillis) / 60_000L
        if (lastGeneratedMillis > 0 && sinceMin < settings.minGapMinutes) {
            return Gate.Blocked("min gap (${settings.minGapMinutes} min)")
        }
        return Gate.Open
    }
}
