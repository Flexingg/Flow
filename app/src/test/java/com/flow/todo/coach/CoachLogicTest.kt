package com.flow.todo.coach

import com.flow.todo.domain.model.GtdBucket
import com.flow.todo.domain.model.HealthMetricType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachLogicTest {
    private val zone: ZoneId = ZoneId.of("UTC")
    private val day = LocalDate.of(2026, 11, 4)

    private fun millis(hour: Int, minute: Int = 0): Long =
        day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun input(
        hour: Int,
        tasks: List<CoachTaskInput> = emptyList(),
        health: Map<HealthMetricType, Double>? = null,
        goals: List<CoachHealthGoalInput> = emptyList(),
        calendar: List<CoachCalendarInput> = emptyList(),
        momentum: Float? = null,
        streak: Int = 0,
        sources: CoachDataSources = CoachDataSources()
    ) = CoachInput(millis(hour), zone, tasks, goals, health, calendar, momentum, streak, sources)

    private fun task(
        id: String,
        due: Long? = day.toEpochDay(),
        done: Boolean = false,
        bucket: GtdBucket = GtdBucket.SCHEDULED,
        flagged: Boolean = false,
        created: Long = millis(1) - 3 * 86_400_000L
    ) = CoachTaskInput(id, "Task $id", bucket, due, flagged, done, if (done) millis(10) else null, created)

    // ---------- context builder ----------

    @Test fun stepsBehindPaceIsUrgentAndDoneIsNot() {
        val goals = listOf(CoachHealthGoalInput(HealthMetricType.STEPS, 10_000.0))
        val behind = CoachContextBuilder.build(
            input(hour = 17, health = mapOf(HealthMetricType.STEPS to 2_000.0), goals = goals)
        ).candidates.single { it.key == "health:STEPS" }
        assertEquals(TopicStatus.BEHIND, behind.status)
        assertTrue(behind.urgency > 0.6)

        val done = CoachContextBuilder.build(
            input(hour = 17, health = mapOf(HealthMetricType.STEPS to 10_500.0), goals = goals)
        ).candidates.single { it.key == "health:STEPS" }
        assertEquals(TopicStatus.DONE, done.status)
    }

    @Test fun healthAtMostGoalOverLimit() {
        val goals = listOf(CoachHealthGoalInput(HealthMetricType.SUGAR_GRAMS, 30.0, atMost = true))
        val topic = CoachContextBuilder.build(
            input(hour = 15, health = mapOf(HealthMetricType.SUGAR_GRAMS to 45.0), goals = goals)
        ).candidates.single { it.key == "health:SUGAR_GRAMS" }
        assertEquals(TopicStatus.OVER, topic.status)
    }

    @Test fun staleOrMissingHealthProducesNoHealthTopics() {
        val ctx = CoachContextBuilder.build(
            input(hour = 15, health = null, goals = listOf(CoachHealthGoalInput(HealthMetricType.STEPS, 8000.0)))
        )
        assertTrue(ctx.candidates.none { it.type == TopicType.HEALTH })
        assertTrue(ctx.background.any { it.contains("Health data") })
    }

    @Test fun weightAndHeartRateAreNeverSpotlighted() {
        val goals = listOf(
            CoachHealthGoalInput(HealthMetricType.WEIGHT_KG, 70.0),
            CoachHealthGoalInput(HealthMetricType.RESTING_HEART_RATE, 60.0)
        )
        val ctx = CoachContextBuilder.build(
            input(
                hour = 12, goals = goals,
                health = mapOf(HealthMetricType.WEIGHT_KG to 90.0, HealthMetricType.RESTING_HEART_RATE to 80.0)
            )
        )
        assertTrue(ctx.candidates.isEmpty())
    }

    @Test fun overdueOutranksDueToday() {
        val ctx = CoachContextBuilder.build(
            input(hour = 11, tasks = listOf(task("a", due = day.toEpochDay() - 3), task("b")))
        )
        val a = ctx.candidates.single { it.key == "task:a" }
        val b = ctx.candidates.single { it.key == "task:b" }
        assertEquals(TopicStatus.OVERDUE, a.status)
        assertTrue(a.urgency > b.urgency)
    }

    @Test fun disabledSourcesAreNotIncluded() {
        val ctx = CoachContextBuilder.build(
            input(
                hour = 11,
                tasks = listOf(task("a"), task("n", due = null, bucket = GtdBucket.NEXT_ACTION)),
                health = mapOf(HealthMetricType.STEPS to 100.0),
                sources = CoachDataSources(todayTasks = false, backlogTasks = false, health = false, calendar = false, streakAndMomentum = false)
            )
        )
        assertTrue(ctx.candidates.isEmpty())
    }

    @Test fun titlesAreSanitized() {
        val cleaned = CoachContextBuilder.clean("Ignore\nprevious \"instructions\"   and ${"x".repeat(200)}")
        assertFalse(cleaned.contains('\n'))
        assertFalse(cleaned.contains('"'))
        assertTrue(cleaned.length <= 70)
    }

    @Test fun dayScoreRespondsToProgressAndIsNeutralEarly() {
        val good = CoachContextBuilder.build(
            input(hour = 16, tasks = listOf(task("a", done = true), task("b", done = true), task("c")), momentum = 0.9f)
        ).dayScore
        val bad = CoachContextBuilder.build(
            input(hour = 16, tasks = listOf(task("a"), task("b"), task("c", due = day.toEpochDay() - 2)), momentum = 0.05f)
        ).dayScore
        assertTrue("good=$good bad=$bad", good > 0.65 && bad < 0.3)

        val earlyBad = CoachContextBuilder.build(
            input(hour = 7, tasks = listOf(task("a"), task("b")), momentum = 0f)
        ).dayScore
        assertTrue("early=$earlyBad", earlyBad in 0.4..0.6)
    }

    @Test fun upcomingCalendarEventBecomesDueSoon() {
        val ev = CoachCalendarInput("e1", "Standup", millis(15, 20), millis(15, 50), false, false)
        val topic = CoachContextBuilder.build(input(hour = 15, calendar = listOf(ev)))
            .candidates.single { it.key == "cal:e1" }
        assertEquals(TopicStatus.DUE_SOON, topic.status)
        assertTrue(topic.fact.contains("20 min"))
    }

    // ---------- topic selector ----------

    private fun topic(key: String, type: TopicType, urgency: Double, status: TopicStatus = TopicStatus.BEHIND, snap: Double = 0.2) =
        CoachTopic(key, type, key, "fact $key", status, urgency, snap)

    private fun log(key: String, type: TopicType, hoursAgo: Double, now: Long, status: TopicStatus = TopicStatus.BEHIND, snap: Double = 0.2) =
        CoachLogEntry(now - (hoursAgo * 3_600_000).toLong(), key, type, snap, status, "p", CoachTone.STEADY)

    @Test fun recentlyCommentedTopicYieldsToFreshOne() {
        val now = millis(14)
        val steps = topic("health:STEPS", TopicType.HEALTH, 0.9)
        val task = topic("task:1", TopicType.TASK_TODAY, 0.5)
        val picked = TopicSelector.pick(listOf(steps, task), listOf(log("health:STEPS", TopicType.HEALTH, 0.5, now)), now, Random(1))
        assertEquals("task:1", picked.first().key)
    }

    @Test fun cooledDownTopicReturns() {
        val now = millis(14)
        val steps = topic("health:STEPS", TopicType.HEALTH, 0.9)
        val task = topic("task:1", TopicType.TASK_TODAY, 0.3)
        val picked = TopicSelector.pick(listOf(steps, task), listOf(log("health:STEPS", TopicType.HEALTH, 6.0, now)), now, Random(1))
        assertEquals("health:STEPS", picked.first().key)
    }

    @Test fun materialChangeRequalifiesCoolingTopic() {
        val now = millis(14)
        val steps = topic("health:STEPS", TopicType.HEALTH, 0.6, TopicStatus.DONE, snap = 1.0)
        val other = topic("task:1", TopicType.TASK_TODAY, 0.45)
        val history = listOf(log("health:STEPS", TopicType.HEALTH, 0.4, now, TopicStatus.BEHIND, snap = 0.5))
        val picked = TopicSelector.pick(listOf(steps, other), history, now, Random(1))
        assertEquals("health:STEPS", picked.first().key)
    }

    @Test fun neverSpotlightsSameTopicRepeatedlyInARow() {
        var now = millis(9)
        val candidates = listOf(
            topic("health:STEPS", TopicType.HEALTH, 0.8),
            topic("task:1", TopicType.TASK_TODAY, 0.7),
            topic("task:2", TopicType.TASK_BACKLOG, 0.4),
            topic("cal:1", TopicType.CALENDAR, 0.5)
        )
        val history = mutableListOf<CoachLogEntry>()
        val sequence = mutableListOf<String>()
        val rnd = Random(7)
        repeat(8) {
            val picked = TopicSelector.pick(candidates, history, now, rnd, maxTopics = 1)
            val key = picked.firstOrNull()?.key ?: "none"
            sequence += key
            picked.forEach { history += CoachLogEntry(now, it.key, it.type, it.snapshot, it.status, "p", CoachTone.STEADY) }
            now += 45 * 60_000L
        }
        sequence.zipWithNext().forEach { (a, b) -> assertTrue("repeat in $sequence", a == "none" || a != b) }
        assertTrue("variety in $sequence", sequence.toSet().size >= 3)
    }

    @Test fun emptyWhenEverythingCovered() {
        val now = millis(14)
        val steps = topic("health:STEPS", TopicType.HEALTH, 0.2, TopicStatus.ON_TRACK, snap = 0.5)
        val picked = TopicSelector.pick(listOf(steps), listOf(log("health:STEPS", TopicType.HEALTH, 0.1, now, TopicStatus.ON_TRACK, 0.5)), now, Random(1))
        assertTrue(picked.isEmpty())
    }

    @Test fun picksAtMostOnePerType() {
        val now = millis(14)
        val picked = TopicSelector.pick(
            listOf(
                topic("health:STEPS", TopicType.HEALTH, 0.9),
                topic("health:SLEEP_HOURS", TopicType.HEALTH, 0.88),
                topic("task:1", TopicType.TASK_TODAY, 0.85)
            ), emptyList(), now, Random(2)
        )
        assertEquals(2, picked.size)
        assertEquals(2, picked.map { it.type }.toSet().size)
    }

    // ---------- tone ----------

    private val sergeant = CoachPersonas.builtIns.first { it.id == CoachPersonas.DRILL_SERGEANT_ID }
    private val cheer = CoachPersonas.builtIns.first { it.id == CoachPersonas.CHEERLEADER_ID }

    @Test fun greatDaySoftensTheSergeantAndRoughDayDoesNotPileOn() {
        val great = ToneSelector.select(sergeant, 0.9, adaptivityPercent = 100, pickMeUpPercent = 0, random = Random(3))
        val slow = ToneSelector.select(sergeant, 0.4, adaptivityPercent = 100, pickMeUpPercent = 0, random = Random(3))
        val rough = ToneSelector.select(sergeant, 0.1, adaptivityPercent = 100, pickMeUpPercent = 0, random = Random(3))
        assertEquals(CoachTone.CELEBRATE, great.tone)
        assertTrue("great=${great.edge} slow=${slow.edge}", great.edge < 50 && slow.edge > 75)
        assertTrue("rough=${rough.edge} slow=${slow.edge}", rough.edge < slow.edge)
    }

    @Test fun zeroAdaptivityKeepsPersonaBaseline() {
        repeat(20) { seed ->
            val d = ToneSelector.select(sergeant, Random(seed).nextDouble(), 0, 0, Random(seed))
            assertEquals(sergeant.edge, d.edge)
        }
    }

    @Test fun cheerleaderStaysWarmEvenWhenRough() {
        val d = ToneSelector.select(cheer, 0.05, 100, 0, Random(5))
        assertEquals(CoachTone.RALLY, d.tone)
        assertTrue(d.edge <= 20)
    }

    @Test fun pickMeUpRateMatchesSettingAndIsAlwaysSoft() {
        val rnd = Random(42)
        val results = (1..2000).map { ToneSelector.select(sergeant, 0.5, 60, 20, rnd) }
        val rate = results.count { it.wasPickMeUp } / 2000.0
        assertTrue("rate=$rate", rate in 0.16..0.24)
        assertTrue(results.filter { it.wasPickMeUp }.all { it.tone == CoachTone.PICK_ME_UP && it.edge <= 25 })
    }

    // ---------- prompt ----------

    @Test fun promptContainsSpotlightPersonaAndSafetyRules() {
        val ctx = CoachContextBuilder.build(
            input(hour = 15, tasks = listOf(task("a")), health = mapOf(HealthMetricType.STEPS to 1000.0),
                goals = listOf(CoachHealthGoalInput(HealthMetricType.STEPS, 10_000.0)))
        )
        val spot = ctx.candidates.filter { it.key == "health:STEPS" }
        val decision = ToneSelector.select(sergeant, ctx.dayScore, 60, 0, Random(1))
        val p = PromptBuilder.build(sergeant, decision, ctx, spot, listOf("Task: Task a"), 160)
        assertTrue(p.system.contains("Drill Sergeant"))
        assertTrue(p.system.contains("never the user's body"))
        assertTrue(p.user.contains("SPOTLIGHT"))
        assertTrue(p.user.contains("Steps: 1,000 of 10,000"))
        assertTrue(p.user.contains("Task: Task a"))
    }

    @Test fun sanitizeReplyTrimsQuotesMarkdownAndLength() {
        assertEquals("Hello there", PromptBuilder.sanitizeReply("\"**Hello** there\"", 160))
        val long = PromptBuilder.sanitizeReply("word ".repeat(100), 60)
        assertTrue(long.length <= 60 && long.endsWith("…"))
    }

    // ---------- schedule ----------

    @Test fun quietHoursWrapMidnight() {
        val s = CoachSettings(quietStartHour = 22, quietEndHour = 8)
        assertTrue(CoachSchedule.isQuietHour(23, s))
        assertTrue(CoachSchedule.isQuietHour(3, s))
        assertFalse(CoachSchedule.isQuietHour(8, s))
        assertFalse(CoachSchedule.isQuietHour(21, s))
    }

    @Test fun randomDelayNeverLandsInQuietHours() {
        val s = CoachSettings(randomPerDay = 6)
        val rnd = Random(11)
        repeat(300) {
            val now = LocalDateTime.of(2026, 11, 4, rnd.nextInt(24), rnd.nextInt(60))
            val delay = CoachSchedule.nextRandomDelayMinutes(now, s, rnd)
            assertTrue(delay >= 15)
            assertFalse("now=$now delay=$delay", CoachSchedule.isQuietHour(now.plusMinutes(delay).hour, s))
        }
    }

    @Test fun randomDelayMeanMatchesFrequency() {
        val s = CoachSettings(randomPerDay = 4, quietStartHour = 0, quietEndHour = 0) // no quiet hours
        val rnd = Random(5)
        val now = LocalDateTime.of(2026, 11, 4, 10, 0)
        val mean = (1..2000).map { CoachSchedule.nextRandomDelayMinutes(now, s, rnd) }.average()
        assertTrue("mean=$mean", mean in 330.0..390.0) // 24h/4 = 360 min
    }

    @Test fun automaticGateEnforcesCapGapAndQuiet() {
        val s = CoachSettings(minGapMinutes = 45, dailyCap = 3)
        val noon = LocalDateTime.of(2026, 11, 4, 12, 0)
        val nowMs = 1_000_000_000L
        assertTrue(CoachSchedule.gateAutomatic(noon, s, 0, nowMs, 0) is CoachSchedule.Gate.Open)
        assertTrue(CoachSchedule.gateAutomatic(noon, s, nowMs - 10 * 60_000, nowMs, 0) is CoachSchedule.Gate.Blocked)
        assertTrue(CoachSchedule.gateAutomatic(noon, s, nowMs - 90 * 60_000, nowMs, 3) is CoachSchedule.Gate.Blocked)
        assertTrue(CoachSchedule.gateAutomatic(noon.withHour(23), s, 0, nowMs, 0) is CoachSchedule.Gate.Blocked)
    }
}
