package com.flow.todo.coach.data

import android.content.Context
import com.flow.todo.coach.CoachDataSources
import com.flow.todo.coach.CoachMessage
import com.flow.todo.coach.CoachPersonas
import com.flow.todo.coach.CoachSettings
import com.flow.todo.coach.CoachTone
import com.flow.todo.coach.CoachTrigger
import com.flow.todo.coach.LlmProvider
import com.flow.todo.domain.model.HealthMetricType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists coach preferences, the latest message (for the card + widget), run counters, and a
 * small cache of the last foreground Health Connect snapshot (background workers cannot read
 * Health Connect without the background-read permission, so they reuse this).
 */
class CoachSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Any()

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<CoachSettings> = _settings.asStateFlow()

    private val _latest = MutableStateFlow(loadLatest())
    val latestMessage: StateFlow<CoachMessage?> = _latest.asStateFlow()

    fun update(transform: (CoachSettings) -> CoachSettings) = synchronized(lock) {
        val next = sanitize(transform(_settings.value))
        _settings.value = next
        prefs.edit().putString(KEY_SETTINGS, settingsToJson(next).toString()).apply()
    }

    // ---- latest message ----

    fun setLatest(message: CoachMessage?) = synchronized(lock) {
        _latest.value = message
        prefs.edit().apply {
            if (message == null) remove(KEY_LATEST) else putString(KEY_LATEST, messageToJson(message).toString())
        }.apply()
    }

    // ---- counters ----

    var lastAttemptMillis: Long
        get() = prefs.getLong(KEY_LAST_ATTEMPT, 0L)
        set(value) { prefs.edit().putLong(KEY_LAST_ATTEMPT, value).apply() }

    fun generatedToday(epochDay: Long): Int =
        if (prefs.getLong(KEY_COUNT_DAY, -1L) == epochDay) prefs.getInt(KEY_COUNT, 0) else 0

    fun recordSuccess(epochDay: Long) = synchronized(lock) {
        val next = generatedToday(epochDay) + 1
        prefs.edit().putLong(KEY_COUNT_DAY, epochDay).putInt(KEY_COUNT, next).apply()
    }

    // ---- health cache ----

    fun saveHealth(values: Map<HealthMetricType, Double>, asOfMillis: Long) {
        val obj = JSONObject()
        values.forEach { (k, v) -> obj.put(k.name, v) }
        prefs.edit().putString(KEY_HEALTH, obj.toString()).putLong(KEY_HEALTH_AT, asOfMillis).apply()
    }

    /** @return cached values and when they were captured, or null if none. */
    fun loadHealth(): Pair<Map<HealthMetricType, Double>, Long>? {
        val raw = prefs.getString(KEY_HEALTH, null) ?: return null
        val at = prefs.getLong(KEY_HEALTH_AT, 0L)
        if (at <= 0L) return null
        return runCatching {
            val obj = JSONObject(raw)
            val map = HealthMetricType.values().mapNotNull { m ->
                if (obj.has(m.name)) m to obj.getDouble(m.name) else null
            }.toMap()
            map to at
        }.getOrNull()
    }

    // ---- (de)serialization ----

    private fun sanitize(s: CoachSettings) = s.copy(
        adaptivityPercent = s.adaptivityPercent.coerceIn(0, 100),
        pickMeUpPercent = s.pickMeUpPercent.coerceIn(0, 50),
        minGapMinutes = s.minGapMinutes.coerceIn(1, 24 * 60),
        intervalHours = s.intervalHours.coerceIn(1, 24),
        randomPerDay = s.randomPerDay.coerceIn(1, 24),
        quietStartHour = s.quietStartHour.coerceIn(0, 23),
        quietEndHour = s.quietEndHour.coerceIn(0, 23),
        dailyCap = s.dailyCap.coerceIn(1, 60),
        maxChars = s.maxChars.coerceIn(60, 320),
        customBaseUrl = s.customBaseUrl.trim()
    )

    private fun loadSettings(): CoachSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return CoachSettings()
        return runCatching {
            val o = JSONObject(raw)
            val d = CoachSettings()
            val overrides = mutableMapOf<LlmProvider, String>()
            o.optJSONObject("models")?.let { m ->
                m.keys().forEach { k ->
                    runCatching { LlmProvider.valueOf(k) }.getOrNull()?.let { overrides[it] = m.optString(k) }
                }
            }
            val triggers = mutableSetOf<CoachTrigger>()
            o.optJSONArray("triggers")?.let { arr ->
                for (i in 0 until arr.length()) runCatching { CoachTrigger.valueOf(arr.getString(i)) }.getOrNull()?.let(triggers::add)
            }
            val src = o.optJSONObject("sources")
            val ds = CoachDataSources()
            sanitize(
                CoachSettings(
                    enabled = o.optBoolean("enabled", d.enabled),
                    provider = runCatching { LlmProvider.valueOf(o.optString("provider")) }.getOrDefault(d.provider),
                    modelOverrides = overrides,
                    customBaseUrl = o.optString("customBaseUrl", d.customBaseUrl),
                    selectedPersonaId = o.optString("persona", d.selectedPersonaId).ifBlank { CoachPersonas.CHEERLEADER_ID },
                    adaptivityPercent = o.optInt("adaptivity", d.adaptivityPercent),
                    pickMeUpPercent = o.optInt("pickMeUp", d.pickMeUpPercent),
                    triggers = if (o.has("triggers")) triggers else d.triggers,
                    minGapMinutes = o.optInt("minGap", d.minGapMinutes),
                    intervalHours = o.optInt("intervalHours", d.intervalHours),
                    randomPerDay = o.optInt("randomPerDay", d.randomPerDay),
                    quietStartHour = o.optInt("quietStart", d.quietStartHour),
                    quietEndHour = o.optInt("quietEnd", d.quietEndHour),
                    dailyCap = o.optInt("dailyCap", d.dailyCap),
                    maxChars = o.optInt("maxChars", d.maxChars),
                    sources = CoachDataSources(
                        todayTasks = src?.optBoolean("todayTasks", ds.todayTasks) ?: ds.todayTasks,
                        backlogTasks = src?.optBoolean("backlogTasks", ds.backlogTasks) ?: ds.backlogTasks,
                        health = src?.optBoolean("health", ds.health) ?: ds.health,
                        calendar = src?.optBoolean("calendar", ds.calendar) ?: ds.calendar,
                        streakAndMomentum = src?.optBoolean("streak", ds.streakAndMomentum) ?: ds.streakAndMomentum
                    )
                )
            )
        }.getOrDefault(CoachSettings())
    }

    private fun settingsToJson(s: CoachSettings): JSONObject = JSONObject().apply {
        put("enabled", s.enabled)
        put("provider", s.provider.name)
        put("models", JSONObject().also { m -> s.modelOverrides.forEach { (k, v) -> m.put(k.name, v) } })
        put("customBaseUrl", s.customBaseUrl)
        put("persona", s.selectedPersonaId)
        put("adaptivity", s.adaptivityPercent)
        put("pickMeUp", s.pickMeUpPercent)
        put("triggers", JSONArray().also { a -> s.triggers.forEach { a.put(it.name) } })
        put("minGap", s.minGapMinutes)
        put("intervalHours", s.intervalHours)
        put("randomPerDay", s.randomPerDay)
        put("quietStart", s.quietStartHour)
        put("quietEnd", s.quietEndHour)
        put("dailyCap", s.dailyCap)
        put("maxChars", s.maxChars)
        put(
            "sources",
            JSONObject()
                .put("todayTasks", s.sources.todayTasks)
                .put("backlogTasks", s.sources.backlogTasks)
                .put("health", s.sources.health)
                .put("calendar", s.sources.calendar)
                .put("streak", s.sources.streakAndMomentum)
        )
    }

    private fun messageToJson(m: CoachMessage): JSONObject = JSONObject()
        .put("text", m.text).put("time", m.timeMillis)
        .put("personaId", m.personaId).put("personaName", m.personaName).put("personaEmoji", m.personaEmoji)
        .put("tone", m.tone.name).put("trigger", m.trigger.name)
        .put("topics", JSONArray().also { a -> m.topicLabels.forEach { a.put(it) } })

    private fun loadLatest(): CoachMessage? {
        val raw = prefs.getString(KEY_LATEST, null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            val topics = o.optJSONArray("topics")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            CoachMessage(
                text = o.getString("text"),
                timeMillis = o.getLong("time"),
                personaId = o.optString("personaId"),
                personaName = o.optString("personaName", "Coach"),
                personaEmoji = o.optString("personaEmoji", "🤖"),
                tone = runCatching { CoachTone.valueOf(o.optString("tone")) }.getOrDefault(CoachTone.STEADY),
                topicLabels = topics,
                trigger = runCatching { CoachTrigger.valueOf(o.optString("trigger")) }.getOrDefault(CoachTrigger.MANUAL)
            )
        }.getOrNull()
    }

    private companion object {
        const val PREFS = "flow_coach_settings"
        const val KEY_SETTINGS = "settings_json"
        const val KEY_LATEST = "latest_message_json"
        const val KEY_LAST_ATTEMPT = "last_attempt_millis"
        const val KEY_COUNT_DAY = "count_day"
        const val KEY_COUNT = "count_today"
        const val KEY_HEALTH = "health_cache_json"
        const val KEY_HEALTH_AT = "health_cache_at"
    }
}
