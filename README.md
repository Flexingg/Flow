# Flow — Living Flow GTD Todo App for Android

**Flow** is a personal Getting Things Done (GTD) Todo application for Android built with **Kotlin** and **Jetpack Compose**, designed in **OPPO's ColorOS 17 "Living Flow / Fluid Design"** language.

## Highlights

- **ColorOS 17 Living Flow UI**:
  - **Luminous Effect**: Restrained frosted glass (`glass.thin`, `glass.regular`, `glass.thick`), vertical gradient **Contour Glow** inner strokes, **Touch Glow** pointer-following highlights, and **Progressive Blur** ramps under floating bars.
  - **Fluid Motion**: Floating glass island navigation bar with horizontal drag-to-switch-tabs, spring-morphing inner indicator pill, and a detached circular glass `+` Quick-Capture button.
  - **Flexible Feedback**: Velocity-aware spring compression (`spring.press`), rubber-band drag stretching, and rebound (`spring.release`) paired with tactile haptics.
- **Barebones GTD Workflow**:
  - **Inbox**: Frictionless capture and clarify flow toward Inbox Zero.
  - **Today & Next**: Daily momentum hero card with luminous "comet" progress bar, **Due Today & Flagged**, and **Next Actions**.
  - **Projects & Someday/Maybe**: Color-coded multi-step project outcomes with progress tracking plus an incubating **Someday / Maybe** section.
- **Accessibility Guardrails**:
  - Built-in toggles in the Settings sheet for **Reduce Transparency** (calm opaque surfaces), **Reduce Motion** (`150ms` cross-fades), **Increased Contrast** (`1.5dp` strokes, $\ge 92\%$ opacity), and **Light Flow / Dark OLED** modes.

See [COLOROS17_FLUID_SPEC_AUDIT.md](./COLOROS17_FLUID_SPEC_AUDIT.md) for the complete design token, motion/haptics, and accessibility audit.

## AI Coach (bring your own key)

A persona-driven coach card on **Today** and a dedicated **AI Coach** home-screen widget that tells you what you're doing well and what to work on next.

- **Models**: Claude (Anthropic), Gemini, DeepSeek, OpenAI, OpenRouter, or any custom OpenAI-compatible endpoint. Keys are stored encrypted with the Android Keystore and sent only to the provider you pick.
- **Personas**: Cheerleader, Drill Sergeant (mean coach), Straight Shooter, Zen Mentor, Sassy Best Friend, Data Nerd, Stoic, plus your own custom personas (name, emoji, personality prompt, baseline "edge").
- **Dynamic mood**: the persona's tone shifts with how your day is going (task completion, health pace, momentum) and a configurable chance of a random pick-me-up, so it is neither always nitpicky nor always cheering.
- **No repeating itself**: a topic log (steps, calories, a specific task, a backlog item, a calendar event…) with per-category cooldowns. A topic re-qualifies early only when it materially changes (e.g. a goal flips to done). The log stores topics, not message text.
- **Triggers** (multi-select): tap to refresh, every app open, every screen/tab open, fixed interval, or random times per day, with a minimum gap, quiet hours and a daily cap to protect your API bill.
- **You control what is sent**: toggle today's tasks, backlog, Health Connect + goals, calendar, streak/momentum. Titles only; "Preview prompt" shows exactly what would be sent.

Code lives in `com.flow.todo.coach` (pure, unit-tested logic at the package root; Android integration in `coach/data` and `coach/work`; UI in `ui/coach`; widget in `widget/FlowCoachWidgetProvider`).
