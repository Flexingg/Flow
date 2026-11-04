package com.flow.todo.coach.data

import android.content.Context
import com.flow.todo.coach.CoachPersona
import com.flow.todo.coach.CoachPersonas
import com.flow.todo.coach.CoachPrompt
import com.flow.todo.coach.CoachSettings
import com.flow.todo.coach.CoachTrigger
import com.flow.todo.coach.LlmProvider
import com.flow.todo.coach.work.CoachScheduler
import com.flow.todo.data.local.FlowDatabase
import com.flow.todo.domain.model.HealthDailySnapshot
import com.flow.todo.widget.FlowCoachWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CoachStatus(
    val isGenerating: Boolean = false,
    val error: String? = null,
    val info: String? = null
)

/**
 * App-lifetime facade used by the UI, the widget and the background worker. Kept separate from
 * the big [com.flow.todo.ui.state.FlowViewModel] so the coach has no blast radius on existing screens.
 */
class CoachController private constructor(private val appContext: Context) {
    private val store = CoachSettingsStore(appContext)
    private val keys = SecureKeyStore(appContext)
    private val dao = CoachDatabase.getInstance(appContext).coachDao()
    private val engine = CoachEngine(appContext, FlowDatabase.getInstance(appContext).flowDao(), dao, store, keys)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: StateFlow<CoachSettings> = store.settings
    val latest = store.latestMessage

    val customPersonas: Flow<List<CoachPersona>> = dao.observePersonas().map { rows -> rows.map { it.toDomain() } }
    val memoryCount: Flow<Int> = dao.observeLogCount()

    private val _status = MutableStateFlow(CoachStatus())
    val status: StateFlow<CoachStatus> = _status.asStateFlow()

    private val _configured = MutableStateFlow(configuredProviders())
    /** Providers that currently have a saved key. */
    val configuredProviders: StateFlow<Set<LlmProvider>> = _configured.asStateFlow()

    private val _settingsRequested = MutableStateFlow(false)
    /** Set by the widget / deep links to ask the UI to open the coach settings sheet. */
    val settingsRequested: StateFlow<Boolean> = _settingsRequested.asStateFlow()

    private data class ScheduleKey(
        val enabled: Boolean,
        val triggers: Set<CoachTrigger>,
        val intervalHours: Int,
        val randomPerDay: Int,
        val quietStart: Int,
        val quietEnd: Int
    )

    init {
        scope.launch {
            settings.map {
                ScheduleKey(it.enabled, it.triggers, it.intervalHours, it.randomPerDay, it.quietStartHour, it.quietEndHour)
            }.distinctUntilChanged().collect { runCatching { CoachScheduler.sync(appContext, store.settings.value) } }
        }
        scope.launch {
            combine(latest, status.map { it.isGenerating }.distinctUntilChanged()) { _, _ -> Unit }
                .collect { FlowCoachWidgetProvider.requestUpdate(appContext) }
        }
    }

    // ---------------- triggers ----------------

    fun onAppOpen(health: HealthDailySnapshot?) = fireForeground(CoachTrigger.APP_OPEN, health)

    fun onScreenOpen(health: HealthDailySnapshot?) = fireForeground(CoachTrigger.SCREEN_OPEN, health)

    /** User tapped the card or widget. */
    fun refreshNow(health: HealthDailySnapshot?) {
        scope.launch { execute(CoachTrigger.MANUAL, health) }
    }

    suspend fun generateNow(health: HealthDailySnapshot?) = execute(CoachTrigger.MANUAL, health)

    suspend fun generateInBackground(trigger: CoachTrigger) = execute(trigger, null)

    private fun fireForeground(trigger: CoachTrigger, health: HealthDailySnapshot?) {
        val s = settings.value
        if (!s.enabled || !s.isEnabled(trigger)) return
        scope.launch { execute(trigger, health) }
    }

    private suspend fun execute(trigger: CoachTrigger, health: HealthDailySnapshot?): CoachEngine.Outcome {
        val outcome = try {
            engine.generate(trigger, health) {
                _status.update { it.copy(isGenerating = true, error = null, info = null) }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            _status.update { it.copy(isGenerating = false) }
            throw e
        } catch (e: Exception) {
            CoachEngine.Outcome.Failed(LlmException(LlmException.Kind.BAD_RESPONSE, "Coach error: ${e.javaClass.simpleName}"))
        }
        // A "busy" skip means another call is mid-flight and will publish its own result.
        if (outcome is CoachEngine.Outcome.Skipped && outcome.reason == CoachEngine.BUSY) return outcome
        _status.value = when (outcome) {
            is CoachEngine.Outcome.Success -> CoachStatus()
            is CoachEngine.Outcome.Failed -> CoachStatus(error = outcome.error.message)
            // Only tell the user about skips they caused by tapping.
            is CoachEngine.Outcome.Skipped -> CoachStatus(info = if (trigger == CoachTrigger.MANUAL) outcome.reason else null)
        }
        return outcome
    }

    // ---------------- settings, keys ----------------

    fun updateSettings(transform: (CoachSettings) -> CoachSettings) = store.update(transform)

    fun saveApiKey(provider: LlmProvider, key: String): Boolean {
        val ok = keys.put(provider, key)
        _configured.value = configuredProviders()
        return ok
    }

    fun removeApiKey(provider: LlmProvider) {
        keys.remove(provider)
        _configured.value = configuredProviders()
    }

    /** Sends a tiny request with the current provider / model / key and reports the result. */
    suspend fun testConnection(): String {
        val s = settings.value
        val key = keys.get(s.provider) ?: return "No API key saved for ${s.provider.label}"
        return try {
            LlmClient.complete(
                LlmRequest(
                    provider = s.provider, baseUrl = s.activeBaseUrl, model = s.activeModel, apiKey = key,
                    system = "You are a connectivity check. Reply with the single word: ok",
                    user = "ping", maxOutputTokens = 32
                )
            )
            "Connected to ${s.provider.label} (${s.activeModel})"
        } catch (e: LlmException) {
            e.message ?: "Connection failed"
        }
    }

    suspend fun previewPrompt(health: HealthDailySnapshot?): CoachPrompt = engine.previewPrompt(health)

    // ---------------- personas, memory ----------------

    fun selectPersona(id: String) = store.update { it.copy(selectedPersonaId = id) }

    suspend fun saveCustomPersona(persona: CoachPersona) {
        dao.upsertPersona(
            CoachPersonaEntity(persona.id, persona.name, persona.emoji, persona.prompt, persona.edge, System.currentTimeMillis())
        )
    }

    suspend fun deleteCustomPersona(id: String) {
        dao.deletePersona(id)
        if (settings.value.selectedPersonaId == id) selectPersona(CoachPersonas.CHEERLEADER_ID)
    }

    fun clearMemory() {
        scope.launch { dao.clearLog() }
    }

    // ---------------- UI plumbing ----------------

    fun requestSettings() { _settingsRequested.value = true }
    fun consumeSettingsRequest() { _settingsRequested.value = false }
    fun dismissMessage() { _status.update { it.copy(error = null, info = null) } }

    private fun configuredProviders(): Set<LlmProvider> = LlmProvider.values().filter { keys.has(it) }.toSet()

    companion object {
        @Volatile
        private var instance: CoachController? = null

        fun get(context: Context): CoachController = instance ?: synchronized(this) {
            instance ?: CoachController(context.applicationContext).also { instance = it }
        }
    }
}
