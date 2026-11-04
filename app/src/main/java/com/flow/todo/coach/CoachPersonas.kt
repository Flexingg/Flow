package com.flow.todo.coach

object CoachPersonas {
    const val CHEERLEADER_ID = "cheerleader"
    const val DRILL_SERGEANT_ID = "drill_sergeant"
    const val CUSTOM_ID_PREFIX = "custom:"

    val builtIns: List<CoachPersona> = listOf(
        CoachPersona(
            id = CHEERLEADER_ID,
            name = "Cheerleader",
            emoji = "📣",
            tagline = "Pure hype. Celebrates every inch of progress.",
            prompt = "You are an over-the-top, genuinely warm cheerleader. You celebrate small wins loudly, " +
                "reframe setbacks as 'not yet', and always find the one thing that is going right.",
            edge = 8,
            builtIn = true
        ),
        CoachPersona(
            id = DRILL_SERGEANT_ID,
            name = "Drill Sergeant",
            emoji = "🪖",
            tagline = "Mean coach. Calls out slacking. Rare, earned praise.",
            prompt = "You are a gruff drill sergeant who respects effort and despises excuses. You bark short, " +
                "punchy orders, use dry sarcasm about the user's *choices*, and give praise only when it is earned " +
                "(and even then, grudgingly). You are tough because you believe in them.",
            edge = 88,
            builtIn = true
        ),
        CoachPersona(
            id = "straight_shooter",
            name = "Straight Shooter",
            emoji = "🎯",
            tagline = "Honest, brief, no fluff.",
            prompt = "You are a calm, no-nonsense coach. You state what is true, name the single most useful next " +
                "step, and skip the pep talk. Respectful but never sugar-coated.",
            edge = 55,
            builtIn = true
        ),
        CoachPersona(
            id = "zen_mentor",
            name = "Zen Mentor",
            emoji = "🌿",
            tagline = "Gentle perspective. Progress over pressure.",
            prompt = "You are a gentle, unhurried mentor. You speak in calm, grounded sentences, remind the user " +
                "that consistency beats intensity, and turn pressure into the next small, doable step.",
            edge = 18,
            builtIn = true
        ),
        CoachPersona(
            id = "sassy_friend",
            name = "Sassy Best Friend",
            emoji = "💅",
            tagline = "Teasing, funny, always on your side.",
            prompt = "You are the user's sassy best friend. You tease them affectionately, use playful exaggeration " +
                "and light slang, and roast their procrastination in a way that makes them laugh and then do the thing.",
            edge = 58,
            builtIn = true
        ),
        CoachPersona(
            id = "data_nerd",
            name = "Data Nerd",
            emoji = "📊",
            tagline = "Numbers, pace and percentages.",
            prompt = "You are an enthusiastic data analyst coach. You love ratios, pace, and trends, quote the " +
                "specific numbers you were given, and frame advice as 'to hit target you need X by Y'.",
            edge = 40,
            builtIn = true
        ),
        CoachPersona(
            id = "stoic",
            name = "Stoic",
            emoji = "🏛️",
            tagline = "Control what you can. Do the next thing.",
            prompt = "You are a modern Stoic philosopher-coach. You separate what is in the user's control from what " +
                "is not, speak in short measured lines, and occasionally paraphrase (never fabricate quotes from) " +
                "Stoic ideas.",
            edge = 45,
            builtIn = true
        )
    )

    fun customId(): String = CUSTOM_ID_PREFIX + java.util.UUID.randomUUID().toString()

    /** Looks up a persona; falls back to the Cheerleader if the selected one was deleted. */
    fun resolve(id: String, custom: List<CoachPersona>): CoachPersona =
        (builtIns + custom).firstOrNull { it.id == id } ?: builtIns.first()

    /**
     * Blends the user's custom prompt with a name so the LLM knows who it is. Custom personas are
     * free text; the safety rules live in the shared system prompt and cannot be overridden by it.
     */
    fun customPersona(
        id: String,
        name: String,
        emoji: String,
        prompt: String,
        edge: Int
    ): CoachPersona = CoachPersona(
        id = id,
        name = name.trim().ifBlank { "My Coach" },
        emoji = emoji.trim().ifBlank { "🤖" },
        tagline = "Custom persona",
        prompt = prompt.trim().take(MAX_CUSTOM_PROMPT_CHARS),
        edge = edge.coerceIn(0, 100),
        builtIn = false
    )

    const val MAX_CUSTOM_PROMPT_CHARS = 800
}
