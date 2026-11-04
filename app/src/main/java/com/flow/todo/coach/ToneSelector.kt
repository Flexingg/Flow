package com.flow.todo.coach

import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Decides how hard or soft the persona should be for *this* message so the coach is neither
 * always nitpicky nor always cheerleading.
 *
 * - The persona supplies a baseline "edge" (0 warm .. 100 brutal).
 * - How the day is going shifts the edge: a great day makes even the drill sergeant grudgingly
 *   warm; a rough day makes tough coaches stop piling on and soft coaches rally harder.
 * - [CoachSettings.adaptivityPercent] controls how far the persona moves from its baseline.
 * - [CoachSettings.pickMeUpPercent] is the chance of a random, metrics-light morale boost.
 */
object ToneSelector {

    fun dayBand(dayScore: Double): DayBand = when {
        dayScore >= 0.75 -> DayBand.GREAT
        dayScore >= 0.50 -> DayBand.SOLID
        dayScore >= 0.30 -> DayBand.SLOW
        else -> DayBand.ROUGH
    }

    fun select(
        persona: CoachPersona,
        dayScore: Double,
        adaptivityPercent: Int,
        pickMeUpPercent: Int,
        random: Random = Random.Default
    ): ToneDecision {
        val band = dayBand(dayScore)
        val base = persona.edge.coerceIn(0, 100)
        val adaptivity = adaptivityPercent.coerceIn(0, 100) / 100.0

        // Random pick-me-up: always soft, regardless of persona, but still in-character.
        if (pickMeUpPercent > 0 && random.nextInt(100) < pickMeUpPercent.coerceIn(0, 100)) {
            val softened = (base * 0.3).roundToInt().coerceAtMost(25)
            return ToneDecision(CoachTone.PICK_ME_UP, softened, band, wasPickMeUp = true)
        }

        val target = when (band) {
            DayBand.GREAT -> base * 0.35            // earned ease
            DayBand.SOLID -> base * 0.80
            DayBand.SLOW -> base * 1.00 + if (base >= 50) 6.0 else 0.0
            DayBand.ROUGH -> if (base >= 50) base * 0.70 else base * 1.0 // don't pile on
        }

        val jitter = if (adaptivity > 0.0) random.nextInt(-8, 9) * adaptivity else 0.0
        val effective = (base + (target - base) * adaptivity + jitter).roundToInt().coerceIn(0, 100)

        val tone = when (band) {
            DayBand.GREAT -> CoachTone.CELEBRATE
            DayBand.SOLID -> if (effective >= 70) CoachTone.NUDGE else CoachTone.STEADY
            DayBand.SLOW -> if (effective >= 60) CoachTone.FIRM else CoachTone.NUDGE
            DayBand.ROUGH -> if (effective >= 60) CoachTone.FIRM else CoachTone.RALLY
        }
        return ToneDecision(tone, effective, band, wasPickMeUp = false)
    }
}
