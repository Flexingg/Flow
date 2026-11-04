package com.flow.todo.coach

import com.flow.todo.domain.model.GtdBucket
import com.flow.todo.domain.model.HealthMetricType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Plain-data inputs so the builder is a pure function (and unit-testable on the JVM). */
data class CoachTaskInput(
    val id: String,
    val title: String,
    val bucket: GtdBucket,
    val dueEpochDay: Long?,
    val isFlagged: Boolean,
    val isCompleted: Boolean,
    val completedAtMillis: Long?,
    val createdAtMillis: Long
)

data class CoachHealthGoalInput(
    val metric: HealthMetricType,
    val target: Double,
    val atMost: Boolean = false
)

data class CoachCalendarInput(
    val id: String,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val isAllDay: Boolean,
    val isCompleted: Boolean
)

data class CoachInput(
    val nowMillis: Long,
    val zone: ZoneId,
    val tasks: List<CoachTaskInput>,
    val healthGoals: List<CoachHealthGoalInput>,
    /** null when health data is unavailable or too stale to trust. */
    val healthValues: Map<HealthMetricType, Double>?,
    val calendar: List<CoachCalendarInput>,
    val momentumToday: Float?,
    val streakDays: Int,
    val sources: CoachDataSources
)

object CoachContextBuilder {

    /** Metrics we never comment on: body weight and heart rate are sensitive and rarely actionable. */
    private val NEVER_SPOTLIGHT = setOf(HealthMetricType.WEIGHT_KG, HealthMetricType.RESTING_HEART_RATE)

    /** Metrics that accumulate across the day (so "pace" applies). Sleep is last night's total. */
    private val NOT_PACED = setOf(HealthMetricType.SLEEP_HOURS)

    /** Metrics worth mentioning even when the user has not set a goal for them. */
    private val DEFAULT_GOALLESS = listOf(
        HealthMetricType.STEPS,
        HealthMetricType.ACTIVE_CALORIES,
        HealthMetricType.EXERCISE_MINUTES,
        HealthMetricType.SLEEP_HOURS,
        HealthMetricType.HYDRATION_LITERS
    )

    private const val DAY_MILLIS = 86_400_000L
    private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.US)

    /** Fraction of a daily total a person would typically have reached by this time (7:00 -> 22:00). */
    fun expectedPace(hourFraction: Double): Double = ((hourFraction - 7.0) / 15.0).coerceIn(0.0, 1.0)

    fun clean(raw: String, maxLen: Int = 70): String {
        val flat = raw.replace(Regex("[\\p{Cntrl}\\s]+"), " ").replace('"', '\'').trim()
        return if (flat.length <= maxLen) flat else flat.take(maxLen - 1).trimEnd() + "…"
    }

    fun build(input: CoachInput): CoachContext {
        val now = Instant.ofEpochMilli(input.nowMillis).atZone(input.zone)
        val hourFraction = now.hour + now.minute / 60.0
        val todayEpochDay = now.toLocalDate().toEpochDay()
        val startOfToday = now.toLocalDate().atStartOfDay(input.zone).toInstant().toEpochMilli()

        val candidates = mutableListOf<CoachTopic>()
        val background = mutableListOf<String>()

        // ---- Tasks ----
        val completedToday = input.tasks.filter {
            it.isCompleted && (it.completedAtMillis ?: 0L) >= startOfToday
        }
        val open = input.tasks.filter { !it.isCompleted }
        val overdue = open.filter { (it.dueEpochDay ?: Long.MAX_VALUE) < todayEpochDay }
        val dueToday = open.filter { it.dueEpochDay == todayEpochDay }

        if (input.sources.todayTasks) {
            val evening = if (hourFraction >= 18.0) 0.15 else 0.0
            overdue.forEach { t ->
                val days = (todayEpochDay - (t.dueEpochDay ?: todayEpochDay)).coerceAtLeast(1)
                candidates += CoachTopic(
                    key = "task:${t.id}",
                    type = TopicType.TASK_TODAY,
                    label = "Task: ${clean(t.title, 30)}",
                    fact = "Task \"${clean(t.title)}\" is overdue by $days day${if (days == 1L) "" else "s"}",
                    status = TopicStatus.OVERDUE,
                    urgency = min(0.95, 0.65 + 0.05 * days + evening),
                    snapshot = 0.0
                )
            }
            dueToday.forEach { t ->
                candidates += CoachTopic(
                    key = "task:${t.id}",
                    type = TopicType.TASK_TODAY,
                    label = "Task: ${clean(t.title, 30)}",
                    fact = "Task \"${clean(t.title)}\" is due today" + if (t.isFlagged) " (flagged)" else "",
                    status = TopicStatus.DUE_SOON,
                    urgency = min(0.9, (if (t.isFlagged) 0.6 else 0.45) + evening),
                    snapshot = 0.0
                )
            }
            completedToday.sortedByDescending { it.completedAtMillis }.take(3).forEach { t ->
                candidates += CoachTopic(
                    key = "task:${t.id}",
                    type = TopicType.TASK_TODAY,
                    label = "Done: ${clean(t.title, 30)}",
                    fact = "Finished \"${clean(t.title)}\" today",
                    status = TopicStatus.DONE,
                    urgency = 0.40,
                    snapshot = 1.0
                )
            }
            background += "Tasks today: ${completedToday.size} done, ${dueToday.size + overdue.size} open" +
                if (overdue.isNotEmpty()) " (${overdue.size} overdue)" else ""
        }

        if (input.sources.backlogTasks) {
            val dueIds = (overdue + dueToday).map { it.id }.toSet()
            val backlog = open.filter {
                it.id !in dueIds && (it.bucket == GtdBucket.NEXT_ACTION || it.bucket == GtdBucket.INBOX || it.isFlagged)
            }
            backlog
                .sortedWith(compareByDescending<CoachTaskInput> { it.isFlagged }.thenBy { it.createdAtMillis })
                .take(4)
                .forEach { t ->
                    val ageDays = ((input.nowMillis - t.createdAtMillis) / DAY_MILLIS).coerceAtLeast(0)
                    val where = if (t.bucket == GtdBucket.INBOX) "the Inbox" else "Next Actions"
                    candidates += CoachTopic(
                        key = "task:${t.id}",
                        type = TopicType.TASK_BACKLOG,
                        label = "Backlog: ${clean(t.title, 30)}",
                        fact = "Backlog item \"${clean(t.title)}\" has sat in $where for $ageDays day${if (ageDays == 1L) "" else "s"}" +
                            if (t.isFlagged) " (flagged)" else "",
                        status = TopicStatus.INFO,
                        urgency = min(0.5, 0.2 + ageDays * 0.01 + if (t.isFlagged) 0.1 else 0.0),
                        snapshot = 0.0
                    )
                }
            val inboxCount = open.count { it.bucket == GtdBucket.INBOX }
            if (inboxCount >= 5) {
                candidates += CoachTopic(
                    key = "general:inbox",
                    type = TopicType.TASK_BACKLOG,
                    label = "Inbox size",
                    fact = "Inbox has $inboxCount unprocessed items",
                    status = TopicStatus.INFO,
                    urgency = min(0.5, 0.2 + inboxCount / 60.0),
                    snapshot = min(1.0, inboxCount / 50.0)
                )
            }
            background += "Backlog: ${open.count { it.bucket == GtdBucket.NEXT_ACTION }} next actions, $inboxCount in inbox"
        }

        // ---- Health ----
        val healthProgressRatios = mutableListOf<Double>()
        val health = input.healthValues
        if (input.sources.health) {
            if (health == null) {
                background += "Health data: unavailable or out of date (do not mention health numbers)"
            } else {
                val goalMetrics = input.healthGoals.filter { it.metric !in NEVER_SPOTLIGHT }
                    .distinctBy { it.metric }
                val goalless = DEFAULT_GOALLESS
                    .filter { m -> goalMetrics.none { it.metric == m } && (health[m] ?: 0.0) > 0.0 }
                    .map { CoachHealthGoalInput(it, it.defaultTarget, atMost = false) }

                (goalMetrics.map { it to true } + goalless.map { it to false }).forEach { (goal, hasGoal) ->
                    val value = health[goal.metric] ?: 0.0
                    val topic = healthTopic(goal, value, hourFraction, hasGoal)
                    candidates += topic.first
                    if (hasGoal) topic.second?.let { healthProgressRatios += it }
                }
            }
        }

        // ---- Calendar ----
        if (input.sources.calendar) {
            val endOfToday = startOfToday + DAY_MILLIS
            input.calendar
                .filter { !it.isAllDay && !it.isCompleted && it.startMillis in startOfToday until endOfToday }
                .forEach { e ->
                    val minsUntil = (e.startMillis - input.nowMillis) / 60_000L
                    val minsSinceEnd = (input.nowMillis - e.endMillis) / 60_000L
                    when {
                        minsUntil in 0..180 -> candidates += CoachTopic(
                            key = "cal:${e.id}",
                            type = TopicType.CALENDAR,
                            label = "Event: ${clean(e.title, 30)}",
                            fact = "Event \"${clean(e.title)}\" starts in ${humanMinutes(minsUntil)}",
                            status = TopicStatus.DUE_SOON,
                            urgency = if (minsUntil <= 45) 0.75 else 0.5,
                            snapshot = 0.0
                        )
                        minsSinceEnd in 30..360 -> candidates += CoachTopic(
                            key = "cal:${e.id}",
                            type = TopicType.CALENDAR,
                            label = "Event: ${clean(e.title, 30)}",
                            fact = "Event \"${clean(e.title)}\" ended ${humanMinutes(minsSinceEnd)} ago and is not checked off",
                            status = TopicStatus.OVERDUE,
                            urgency = 0.30,
                            snapshot = 0.0
                        )
                    }
                }
        }

        // ---- Streak / momentum ----
        if (input.sources.streakAndMomentum) {
            if (input.streakDays >= 2) {
                candidates += CoachTopic(
                    key = "general:streak",
                    type = TopicType.GENERAL,
                    label = "Streak",
                    fact = "${input.streakDays}-day streak of completing at least one task",
                    status = TopicStatus.WINNING,
                    urgency = min(0.6, 0.3 + input.streakDays / 30.0),
                    snapshot = min(1.0, input.streakDays / 30.0)
                )
            }
            background += "Streak: ${input.streakDays} day${if (input.streakDays == 1) "" else "s"}"
        }

        // ---- Day score ----
        val taskScore: Double? = run {
            val denom = completedToday.size + dueToday.size + 0.5 * overdue.size
            if (input.sources.todayTasks && denom > 0.0) completedToday.size / denom else null
        }
        val healthScore: Double? = healthProgressRatios.takeIf { it.isNotEmpty() }?.average()
        val momentum: Double? = if (input.sources.streakAndMomentum) input.momentumToday?.toDouble() else null

        var weighted = 0.0
        var weights = 0.0
        taskScore?.let { weighted += it * 0.45; weights += 0.45 }
        healthScore?.let { weighted += it * 0.35; weights += 0.35 }
        momentum?.let { weighted += it.coerceIn(0.0, 1.0) * 0.20; weights += 0.20 }
        val raw = if (weights > 0.0) weighted / weights else 0.5
        // Early in the day there is not enough evidence to call it good or bad.
        val confidence = ((hourFraction - 7.0) / 8.0).coerceIn(0.10, 1.0)
        val dayScore = (0.5 + (raw - 0.5) * confidence).coerceIn(0.0, 1.0)

        return CoachContext(
            nowLabel = "${now.format(TIME_FORMAT)} (${partOfDay(now.hour)})",
            hourOfDay = now.hour,
            dayScore = dayScore,
            dayBand = ToneSelector.dayBand(dayScore),
            candidates = candidates,
            background = background
        )
    }

    /**
     * @return the topic and (for goal-backed metrics) a 0..1 "on pace" ratio feeding the day score.
     */
    private fun healthTopic(
        goal: CoachHealthGoalInput,
        value: Double,
        hourFraction: Double,
        hasGoal: Boolean
    ): Pair<CoachTopic, Double?> {
        val metric = goal.metric
        val target = goal.target.takeIf { it > 0.0 } ?: metric.defaultTarget
        val progress = value / target
        val paced = metric !in NOT_PACED
        val expected = if (paced) expectedPace(hourFraction) else 1.0
        val shown = "${metric.label}: ${metric.formatValue(value)} of ${metric.formatValue(target)} ${metric.unit}" +
            " (${(progress * 100).toInt()}%)"
        val damp = if (hasGoal) 1.0 else 0.6 // un-goaled metrics are nice-to-mention, not nag-worthy

        val status: TopicStatus
        val urgency: Double
        val fact: String
        val ratio: Double?

        if (goal.atMost) {
            status = when {
                value > target -> TopicStatus.OVER
                progress >= 0.85 -> TopicStatus.BEHIND
                else -> TopicStatus.ON_TRACK
            }
            urgency = when (status) {
                TopicStatus.OVER -> 0.85
                TopicStatus.BEHIND -> 0.5
                else -> 0.15
            }
            fact = shown + when (status) {
                TopicStatus.OVER -> " - limit exceeded"
                TopicStatus.BEHIND -> " - close to the limit"
                else -> " - within limit"
            }
            ratio = if (value > target) 0.3 else 1.0
        } else {
            val soonEnough = max(expected, 0.25)
            if (value >= target) {
                status = TopicStatus.DONE
                urgency = 0.50
                fact = "$shown - goal reached"
            } else if (!paced) {
                status = if (progress >= 0.85) TopicStatus.ON_TRACK else TopicStatus.BEHIND
                urgency = if (status == TopicStatus.BEHIND) (if (hourFraction < 12) 0.5 else 0.3) else 0.2
                fact = "$shown - last night's total"
            } else {
                status = when {
                    progress >= expected + 0.10 -> TopicStatus.WINNING
                    progress >= expected - 0.10 -> TopicStatus.ON_TRACK
                    else -> TopicStatus.BEHIND
                }
                urgency = when (status) {
                    TopicStatus.WINNING -> 0.35
                    TopicStatus.ON_TRACK -> 0.20
                    else -> min(0.95, 0.5 + (expected - progress).coerceAtLeast(0.0) * 0.8)
                }
                fact = "$shown - typical pace by now is about ${(expected * 100).toInt()}%"
            }
            ratio = min(1.0, progress / soonEnough)
        }

        return CoachTopic(
            key = "health:${metric.name}",
            type = TopicType.HEALTH,
            label = metric.label,
            fact = fact,
            status = status,
            urgency = urgency * damp,
            snapshot = min(1.5, abs(progress))
        ) to ratio
    }

    private fun humanMinutes(minutes: Long): String = when {
        minutes < 1 -> "moments"
        minutes < 60 -> "$minutes min"
        else -> {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0L) "${h}h" else "${h}h ${m}m"
        }
    }

    private fun partOfDay(hour: Int): String = when {
        hour < 5 -> "night"
        hour < 12 -> "morning"
        hour < 17 -> "afternoon"
        hour < 21 -> "evening"
        else -> "night"
    }
}
