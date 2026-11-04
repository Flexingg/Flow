package com.flow.todo.coach

/**
 * Wire protocols the BYOK client can speak. Most vendors (DeepSeek, OpenAI, OpenRouter, Groq,
 * Ollama, LM Studio, ...) are OpenAI-compatible, so three adapters cover "Claude, Gemini,
 * DeepSeek, etc."
 */
enum class WireFormat { ANTHROPIC, GEMINI, OPENAI_COMPATIBLE }

enum class LlmProvider(
    val label: String,
    val wireFormat: WireFormat,
    val defaultModel: String,
    val defaultBaseUrl: String,
    val suggestedModels: List<String>,
    val keyHint: String
) {
    ANTHROPIC(
        "Claude (Anthropic)", WireFormat.ANTHROPIC,
        "claude-haiku-5-5", "https://api.anthropic.com",
        listOf("claude-haiku-5-5", "claude-sonnet-5-5"), "sk-ant-…"
    ),
    GEMINI(
        "Gemini (Google)", WireFormat.GEMINI,
        "gemini-2.5-flash", "https://generativelanguage.googleapis.com",
        listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.5-pro"), "AIza…"
    ),
    DEEPSEEK(
        "DeepSeek", WireFormat.OPENAI_COMPATIBLE,
        "deepseek-chat", "https://api.deepseek.com",
        listOf("deepseek-chat", "deepseek-reasoner"), "sk-…"
    ),
    OPENAI(
        "OpenAI", WireFormat.OPENAI_COMPATIBLE,
        "gpt-4o-mini", "https://api.openai.com/v1",
        listOf("gpt-4o-mini", "gpt-4o"), "sk-…"
    ),
    OPENROUTER(
        "OpenRouter", WireFormat.OPENAI_COMPATIBLE,
        "openrouter/auto", "https://openrouter.ai/api/v1",
        listOf("openrouter/auto"), "sk-or-…"
    ),
    CUSTOM(
        "Custom (OpenAI-compatible)", WireFormat.OPENAI_COMPATIBLE,
        "", "",
        emptyList(), "API key"
    );

    /** Providers that need the user to supply a base URL. */
    val requiresBaseUrl: Boolean get() = this == CUSTOM
}

/** What can cause the coach to speak. MANUAL (tap the card / widget) is always available. */
enum class CoachTrigger(val label: String, val description: String) {
    MANUAL("Tap to refresh", "Only when you tap the card or widget"),
    APP_OPEN("Every app open", "Each time Flow comes to the foreground"),
    SCREEN_OPEN("Every screen open", "Each time you switch to a different tab"),
    INTERVAL("Fixed interval", "Every N hours in the background"),
    RANDOM("Random times", "A few unpredictable times a day in the background");

    /** True for triggers that fire while the app is on screen (no WorkManager needed). */
    val isForeground: Boolean get() = this == APP_OPEN || this == SCREEN_OPEN
    val isBackground: Boolean get() = this == INTERVAL || this == RANDOM
}

/** Which slices of the user's data may be sent to the chosen LLM provider. */
data class CoachDataSources(
    val todayTasks: Boolean = true,
    val backlogTasks: Boolean = true,
    val health: Boolean = true,
    val calendar: Boolean = true,
    val streakAndMomentum: Boolean = true
)

data class CoachSettings(
    val enabled: Boolean = false,
    val provider: LlmProvider = LlmProvider.ANTHROPIC,
    /** Per-provider model override; blank/missing -> provider default. */
    val modelOverrides: Map<LlmProvider, String> = emptyMap(),
    val customBaseUrl: String = "",
    val selectedPersonaId: String = CoachPersonas.CHEERLEADER_ID,

    /** 0 = persona never changes tone, 100 = tone follows how the day is going. */
    val adaptivityPercent: Int = 60,
    /** Chance (0..100) that any message is an unprompted "pick-me-up" instead of a status comment. */
    val pickMeUpPercent: Int = 12,

    val triggers: Set<CoachTrigger> = setOf(CoachTrigger.MANUAL, CoachTrigger.APP_OPEN),
    /** Minimum minutes between automatic messages (manual taps ignore this). */
    val minGapMinutes: Int = 45,
    val intervalHours: Int = 4,
    val randomPerDay: Int = 4,
    val quietStartHour: Int = 22,
    val quietEndHour: Int = 8,
    /** Hard ceiling on automatic LLM calls per day (protects the user's API bill). */
    val dailyCap: Int = 12,

    val maxChars: Int = 160,
    val sources: CoachDataSources = CoachDataSources()
) {
    val activeModel: String
        get() = modelOverrides[provider]?.takeIf { it.isNotBlank() } ?: provider.defaultModel

    val activeBaseUrl: String
        get() = if (provider.requiresBaseUrl) customBaseUrl.trim() else provider.defaultBaseUrl

    /** MANUAL is implicitly always on. */
    fun isEnabled(trigger: CoachTrigger): Boolean = trigger == CoachTrigger.MANUAL || trigger in triggers
}

/**
 * A voice for the coach.
 *
 * @param edge baseline harshness. 0 = pure warmth, 50 = direct and balanced, 100 = brutal tough love.
 *             The tone selector moves the *effective* edge up and down around this baseline.
 */
data class CoachPersona(
    val id: String,
    val name: String,
    val emoji: String,
    val tagline: String,
    val prompt: String,
    val edge: Int,
    val builtIn: Boolean = false
)

/** How the day is going, used to modulate the persona. */
enum class DayBand(val label: String) {
    GREAT("going great"),
    SOLID("going fine"),
    SLOW("a bit slow"),
    ROUGH("rough so far")
}

enum class CoachTone(val label: String) {
    CELEBRATE("Celebrating"),
    STEADY("Steady"),
    NUDGE("Nudge"),
    FIRM("Firm push"),
    RALLY("Rallying"),
    PICK_ME_UP("Pick-me-up")
}

data class ToneDecision(
    val tone: CoachTone,
    /** Effective harshness 0..100 for this one message. */
    val edge: Int,
    val dayBand: DayBand,
    val wasPickMeUp: Boolean
)

enum class TopicType(val label: String, val cooldownHours: Double) {
    HEALTH("Health", 3.0),
    TASK_TODAY("Today's task", 4.0),
    TASK_BACKLOG("Backlog", 24.0),
    CALENDAR("Calendar", 2.0),
    GENERAL("General", 6.0)
}

enum class TopicStatus { DONE, WINNING, ON_TRACK, BEHIND, OVER, OVERDUE, DUE_SOON, INFO }

/**
 * Something the coach *could* comment on. Candidates are generated deterministically from the
 * user's data; the [TopicSelector] decides which ones get the spotlight, using the comment log
 * so the coach doesn't keep nagging about the same thing.
 */
data class CoachTopic(
    /** Stable identity used in the log, e.g. "health:STEPS", "task:<uuid>", "cal:<id>", "general:inbox". */
    val key: String,
    val type: TopicType,
    val label: String,
    val fact: String,
    val status: TopicStatus,
    /** 0..1 intrinsic importance right now, before the log is considered. */
    val urgency: Double,
    /** Numeric state at time of comment; a big move since last mention re-qualifies a cooling topic. */
    val snapshot: Double = 0.0
)

/** One row of "what the coach commented on". Deliberately stores topics, not message text. */
data class CoachLogEntry(
    val timeMillis: Long,
    val topicKey: String,
    val type: TopicType,
    val snapshot: Double,
    val status: TopicStatus,
    val personaId: String,
    val tone: CoachTone
)

data class CoachMessage(
    val text: String,
    val timeMillis: Long,
    val personaId: String,
    val personaName: String,
    val personaEmoji: String,
    val tone: CoachTone,
    val topicLabels: List<String>,
    val trigger: CoachTrigger
)

/** Everything the prompt needs about "now". Built by [CoachContextBuilder]. */
data class CoachContext(
    val nowLabel: String,
    val hourOfDay: Int,
    val dayScore: Double,
    val dayBand: DayBand,
    val candidates: List<CoachTopic>,
    val background: List<String>
)
