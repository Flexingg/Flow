package com.flow.todo.coach.data

import android.content.Context
import com.flow.todo.coach.CoachCalendarInput
import com.flow.todo.coach.CoachContextBuilder
import com.flow.todo.coach.CoachHealthGoalInput
import com.flow.todo.coach.CoachInput
import com.flow.todo.coach.CoachMessage
import com.flow.todo.coach.CoachPersona
import com.flow.todo.coach.CoachPersonas
import com.flow.todo.coach.CoachPrompt
import com.flow.todo.coach.CoachSchedule
import com.flow.todo.coach.CoachTaskInput
import com.flow.todo.coach.CoachTopic
import com.flow.todo.coach.CoachTrigger
import com.flow.todo.coach.PromptBuilder
import com.flow.todo.coach.ToneDecision
import com.flow.todo.coach.ToneSelector
import com.flow.todo.coach.TopicSelector
import com.flow.todo.data.local.FlowDao
import com.flow.todo.domain.model.GoalCategory
import com.flow.todo.domain.model.GoalComparison
import com.flow.todo.domain.model.GoalPeriod
import com.flow.todo.domain.model.GtdBucket
import com.flow.todo.domain.model.HealthDailySnapshot
import com.flow.todo.domain.model.HealthMetricType
import com.flow.todo.widget.FlowCoachWidgetProvider
import kotlinx.coroutines.sync.Mutex
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/**
 * Orchestrates one coach message:
 *   gate -> gather data -> score day -> choose tone -> choose spotlight topics (using the log)
 *   -> build prompt -> call the user's LLM -> sanitize -> log topics -> publish.
 */
class CoachEngine(
    private val appContext: Context,
    private val flowDao: FlowDao,
    private val coachDao: CoachDao,
    private val store: CoachSettingsStore,
    private val keys: SecureKeyStore
) {
    sealed class Outcome {
        data class Success(val message: CoachMessage) : Outcome()
        data class Skipped(val reason: String) : Outcome()
        data class Failed(val error: LlmException) : Outcome()
    }

    private class Prepared(
        val persona: CoachPersona,
        val decision: ToneDecision,
        val spotlight: List<CoachTopic>,
        val prompt: CoachPrompt
    )

    private val mutex = Mutex()

    suspend fun generate(
        trigger: CoachTrigger,
        health: HealthDailySnapshot? = null,
        onRequestStart: () -> Unit = {}
    ): Outcome {
        val settings = store.settings.value
        if (!settings.enabled) return Outcome.Skipped("Coach is turned off")
        if (!settings.isEnabled(trigger)) return Outcome.Skipped("${trigger.label} is not enabled")

        val zone = ZoneId.systemDefault()
        val nowMillis = System.currentTimeMillis()
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val epochDay = now.toLocalDate().toEpochDay()
        val generatedToday = store.generatedToday(epochDay)

        if (trigger == CoachTrigger.MANUAL) {
            if (generatedToday >= HARD_DAILY_CEILING) return Outcome.Skipped("Hard daily limit ($HARD_DAILY_CEILING) reached")
        } else {
            val gate = CoachSchedule.gateAutomatic(now.toLocalDateTime(), settings, store.lastAttemptMillis, nowMillis, generatedToday)
            if (gate is CoachSchedule.Gate.Blocked) return Outcome.Skipped(gate.reason)
        }

        val apiKey = keys.get(settings.provider)
            ?: return Outcome.Failed(LlmException(LlmException.Kind.NO_KEY, "Add an API key for ${settings.provider.label}"))

        if (!mutex.tryLock()) return Outcome.Skipped(BUSY)
        try {
            store.lastAttemptMillis = nowMillis // gap applies to attempts so a failing API isn't hammered
            val prepared = prepare(health, nowMillis, zone)

            onRequestStart()
            val reply = try {
                LlmClient.complete(
                    LlmRequest(
                        provider = settings.provider,
                        baseUrl = settings.activeBaseUrl,
                        model = settings.activeModel,
                        apiKey = apiKey,
                        system = prepared.prompt.system,
                        user = prepared.prompt.user,
                        maxOutputTokens = 400
                    )
                )
            } catch (e: LlmException) {
                return Outcome.Failed(e)
            }

            val text = PromptBuilder.sanitizeReply(reply, settings.maxChars)
            if (text.isBlank()) {
                return Outcome.Failed(LlmException(LlmException.Kind.BAD_RESPONSE, "Empty reply"))
            }

            val message = CoachMessage(
                text = text,
                timeMillis = nowMillis,
                personaId = prepared.persona.id,
                personaName = prepared.persona.name,
                personaEmoji = prepared.persona.emoji,
                tone = prepared.decision.tone,
                topicLabels = prepared.spotlight.map { it.label },
                trigger = trigger
            )

            if (prepared.spotlight.isNotEmpty()) {
                coachDao.insertLog(
                    prepared.spotlight.map {
                        CoachLogEntity(
                            timeMillis = nowMillis,
                            topicKey = it.key,
                            label = it.label,
                            typeName = it.type.name,
                            snapshot = it.snapshot,
                            statusName = it.status.name,
                            personaId = prepared.persona.id,
                            toneName = prepared.decision.tone.name
                        )
                    }
                )
            }
            coachDao.pruneLog(nowMillis - LOG_RETENTION_MILLIS)
            store.recordSuccess(epochDay)
            store.setLatest(message)
            FlowCoachWidgetProvider.requestUpdate(appContext)
            return Outcome.Success(message)
        } finally {
            mutex.unlock()
        }
    }

    /** The exact system + user prompt that would be sent right now (no network call, nothing logged). */
    suspend fun previewPrompt(health: HealthDailySnapshot?): CoachPrompt {
        val prepared = prepare(health, System.currentTimeMillis(), ZoneId.systemDefault())
        return prepared.prompt
    }

    private suspend fun prepare(health: HealthDailySnapshot?, nowMillis: Long, zone: ZoneId): Prepared {
        val settings = store.settings.value
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().toEpochDay()

        val tasks = flowDao.getAllTasks().map { t ->
            CoachTaskInput(
                id = t.id,
                title = t.title,
                bucket = runCatching { GtdBucket.valueOf(t.bucketName) }.getOrDefault(GtdBucket.INBOX),
                dueEpochDay = t.dueDateEpochDay,
                isFlagged = t.isFlagged,
                isCompleted = t.isCompleted,
                completedAtMillis = t.completedAt,
                createdAtMillis = t.createdAt
            )
        }
        val healthGoals = flowDao.getAllGoals().mapNotNull { g ->
            if (g.categoryName != GoalCategory.HEALTH_CONNECT.name || g.periodName != GoalPeriod.DAILY.name) return@mapNotNull null
            val metric = g.healthMetricName?.let { runCatching { HealthMetricType.valueOf(it) }.getOrNull() } ?: return@mapNotNull null
            CoachHealthGoalInput(metric, g.targetValue, atMost = g.comparisonName == GoalComparison.AT_MOST.name)
        }
        val calendar = flowDao.getAllCalendarEvents().map {
            CoachCalendarInput(it.id, it.title, it.startEpochMillis, it.endEpochMillis, it.isAllDay, it.isCompleted)
        }
        val history = flowDao.getAllDailyHistory()
        val momentum = history.firstOrNull { it.epochDay == today }?.momentumScore
        val tasksByDay = history.associate { it.epochDay to it.tasksCompletedCount }

        val input = CoachInput(
            nowMillis = nowMillis,
            zone = zone,
            tasks = tasks,
            healthGoals = healthGoals,
            healthValues = resolveHealth(health, nowMillis),
            calendar = calendar,
            momentumToday = momentum,
            streakDays = streak(tasksByDay, today),
            sources = settings.sources
        )
        val context = CoachContextBuilder.build(input)

        val recent = coachDao.logSince(nowMillis - HISTORY_WINDOW_MILLIS)
        val log = recent.map { it.toDomain() }
        val rnd = Random.Default

        val persona = CoachPersonas.resolve(settings.selectedPersonaId, coachDao.getPersonas().map { it.toDomain() })
        val decision = ToneSelector.select(persona, context.dayScore, settings.adaptivityPercent, settings.pickMeUpPercent, rnd)

        // A pick-me-up leans on the persona, not the metrics: keep at most one low-key spotlight.
        val maxTopics = if (decision.wasPickMeUp) 1 else 2
        val spotlight = TopicSelector.pick(context.candidates, log, nowMillis, rnd, maxTopics)

        val recentLabels = recent.filter { nowMillis - it.timeMillis <= 12 * HOUR }
            .map { it.label }.distinct().take(8)

        val prompt = PromptBuilder.build(persona, decision, context, spotlight, recentLabels, settings.maxChars)
        return Prepared(persona, decision, spotlight, prompt)
    }

    /**
     * Foreground callers pass a live snapshot which is cached. Background workers (which cannot
     * read Health Connect) fall back to the cache; if it is stale we send no health data at all
     * rather than misleading zeros.
     */
    private fun resolveHealth(live: HealthDailySnapshot?, nowMillis: Long): Map<HealthMetricType, Double>? {
        if (live != null && live.isSdkAvailable && live.hasAnyPermission && live.lastSyncedMillis > 0L) {
            store.saveHealth(live.metrics, live.lastSyncedMillis)
            return live.metrics
        }
        val cached = store.loadHealth() ?: return null
        return if (nowMillis - cached.second <= HEALTH_MAX_AGE_MILLIS) cached.first else null
    }

    private fun streak(tasksByDay: Map<Long, Int>, today: Long): Int {
        var day = if ((tasksByDay[today] ?: 0) > 0) today else today - 1
        var n = 0
        while ((tasksByDay[day] ?: 0) > 0) { n++; day-- }
        return n
    }

    companion object {
        const val BUSY = "Already generating"
        private const val HOUR = 3_600_000L
        private const val HISTORY_WINDOW_MILLIS = 3 * 24 * HOUR
        private const val LOG_RETENTION_MILLIS = 14 * 24 * HOUR
        private const val HEALTH_MAX_AGE_MILLIS = 8 * HOUR
        private const val HARD_DAILY_CEILING = 60
    }
}
