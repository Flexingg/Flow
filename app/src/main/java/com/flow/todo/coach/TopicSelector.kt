package com.flow.todo.coach

import kotlin.math.abs
import kotlin.random.Random

/**
 * Picks which 1-2 topics the coach should talk about, using the comment log so it doesn't
 * repeat itself.
 *
 * Score = (baseline + urgency) x freshness^2 x repeatDamping x sameTypeDamping + tiny jitter
 *
 * - freshness: time since the topic was last mentioned relative to its type's cooldown.
 *   Never mentioned -> 1.0. A cooling topic is strongly damped...
 * - ...unless it has changed since the last mention: progress moved by 20%+ (freshness floor 0.75),
 *   or it just flipped to DONE / OVER / OVERDUE, which is news worth reacting to immediately
 *   (fully fresh and exempt from the repeat / same-category damping).
 * - repeatDamping: a topic mentioned many times in the last 24h gets progressively quieter.
 * - sameTypeDamping: avoid commenting on the same category twice in a row (steps, then calories,
 *   then a task feels fresher than steps, steps, steps).
 */
object TopicSelector {
    private const val BASELINE = 0.15
    private const val MATERIAL_CHANGE = 0.20
    private const val MIN_SCORE = 0.10
    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    private const val HOUR_MILLIS = 60 * 60 * 1000.0

    data class Ranked(val topic: CoachTopic, val score: Double)

    fun rank(
        candidates: List<CoachTopic>,
        history: List<CoachLogEntry>,
        nowMillis: Long,
        random: Random = Random.Default
    ): List<Ranked> {
        val lastByKey = HashMap<String, CoachLogEntry>()
        val count24h = HashMap<String, Int>()
        for (entry in history) {
            val existing = lastByKey[entry.topicKey]
            if (existing == null || entry.timeMillis > existing.timeMillis) lastByKey[entry.topicKey] = entry
            if (nowMillis - entry.timeMillis in 0 until DAY_MILLIS) {
                count24h[entry.topicKey] = (count24h[entry.topicKey] ?: 0) + 1
            }
        }
        val lastType = history.maxByOrNull { it.timeMillis }?.type

        return candidates.map { c ->
            val last = lastByKey[c.key]
            var freshness = if (last == null) {
                1.0
            } else {
                val hours = (nowMillis - last.timeMillis).coerceAtLeast(0L) / HOUR_MILLIS
                (hours / c.type.cooldownHours).coerceIn(0.0, 1.0)
            }
            val statusFlip = last != null && hasStatusFlip(c, last)
            if (statusFlip) freshness = 1.0
            else if (last != null && abs(c.snapshot - last.snapshot) >= MATERIAL_CHANGE) freshness = maxOf(freshness, 0.75)

            val repeatDamping = if (statusFlip) 1.0 else 1.0 / (1.0 + 0.6 * (count24h[c.key] ?: 0))
            val sameTypeDamping =
                if (!statusFlip && c.type == lastType && c.type != TopicType.GENERAL) 0.8 else 1.0
            val score = (BASELINE + c.urgency.coerceIn(0.0, 1.0)) *
                freshness * freshness * repeatDamping * sameTypeDamping +
                random.nextDouble() * 0.04
            Ranked(c, score)
        }.sortedByDescending { it.score }
    }

    /**
     * @return up to [maxTopics] topics. Empty means "everything has been covered recently";
     * the caller should ask for a light, general remark instead of re-hashing a topic.
     */
    fun pick(
        candidates: List<CoachTopic>,
        history: List<CoachLogEntry>,
        nowMillis: Long,
        random: Random = Random.Default,
        maxTopics: Int = 2
    ): List<CoachTopic> {
        val ranked = rank(candidates, history, nowMillis, random)
        val best = ranked.firstOrNull() ?: return emptyList()
        if (best.score < MIN_SCORE) return emptyList()

        val picks = mutableListOf(best.topic)
        for (r in ranked.drop(1)) {
            if (picks.size >= maxTopics) break
            if (r.score >= MIN_SCORE && r.score >= best.score * 0.55 && picks.none { it.type == r.topic.type }) {
                picks += r.topic
            }
        }
        return picks
    }

    private fun hasStatusFlip(topic: CoachTopic, last: CoachLogEntry): Boolean =
        topic.status != last.status &&
            topic.status in setOf(TopicStatus.DONE, TopicStatus.OVER, TopicStatus.OVERDUE)
}
