# Flow: a calendar-first day planner with a strong to-do list

**Flow** is a calendar-first day planner with a built-in to-do list for Android built with **Kotlin** and **Jetpack Compose**, designed in **OPPO's ColorOS 17 "Living Flow / Fluid Design"** language.

## Highlights

- **ColorOS 17 Living Flow UI**:
  - **Luminous Effect**: Restrained frosted glass (`glass.thin`, `glass.regular`, `glass.thick`), vertical gradient **Contour Glow** inner strokes, **Touch Glow** pointer-following highlights, and **Progressive Blur** ramps under floating bars.
  - **Fluid Motion**: Floating glass island navigation bar with horizontal drag-to-switch-tabs, spring-morphing inner indicator pill, and a detached circular glass `+` Quick-Capture button.
  - **Flexible Feedback**: Velocity-aware spring compression (`spring.press`), rubber-band drag stretching, and rebound (`spring.release`) paired with tactile haptics.
- **Day (home)**: week strip (swipe between weeks, tap the month for a month view), all-day chips, and an hourly **timeline** with a live now line.
  - Events and time-blocked tasks side by side (overlaps get lanes); **free time** between them is labeled ("1h 30m free"); tap it to add a task, add a calendar event, or drop in an existing to-do.
  - **Long-press a block to move it** (15-minute snaps), **drag its bottom edge to resize**, and **long-press a to-do in the "To schedule" tray and drop it on the timeline** to time-block it.
  - **Two-way calendar sync** through Android's calendar provider: moving, resizing, adding or deleting events writes to Google Calendar (and other synced accounts); changes made there show up live. Repeating events are read-only here.
- **To-Do**: the due-date list (Overdue, Today, Upcoming, No date, Done today) or an **Eisenhower matrix** (★ = important; urgent = due by tomorrow unless moved).
- **Quick Capture** with smart text: "dentist friday at 3:30pm for 45 min", "gym every monday", "call mom tomorrow !flag", "pack // passport // charger". Time and length chips, and multi-line batches.
- **Goals** (tasks, Health Connect, app-usage limits), the **AI Coach**, system color sync, and three widgets including a **mini day timeline**.
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

## Calendar widget

**Flow Calendar** home-screen widget: today's agenda on a system-color glass card. Calendar events (with their calendar color, "now" highlight) and due/overdue tasks, filled out with tomorrow's events when today is light. Tap a row's circle to check it off from the home screen; tap anywhere else to open the Calendar tab; "+" opens Quick Capture. Recurring tasks open the app (so the next occurrence is scheduled). Agenda logic: `widget/agenda/CalendarAgenda.kt` (unit-tested).
