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
