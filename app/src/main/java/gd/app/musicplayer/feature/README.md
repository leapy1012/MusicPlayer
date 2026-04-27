# Feature layer

Feature packages own screens and UI-specific state.

Target pattern:

```text
feature/<feature-name>/
  <Feature>Activity.kt or <Feature>Fragment.kt
  <Feature>ViewModel.kt
  <Feature>UiState.kt
  <Feature>Action.kt
  <Feature>Event.kt
```

Responsibilities:

- Render `UiState`.
- Send user actions to the ViewModel.
- Perform navigation and permission prompts.
- Keep business logic in use cases, not Activities.

ViewModel rules:

- Expose immutable `StateFlow<UiState>`.
- Use `SharedFlow` or `Channel` for one-time events.
- Call domain use cases.
- Do not hold Android `View` or `Context` references unless using `@ApplicationContext` for a justified reason.
