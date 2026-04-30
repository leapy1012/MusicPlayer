# AGENTS.md

## Project Rule

This project is a modern offline Android music player app.

Always refactor and write code using:

- Kotlin
- MVVM architecture
- Hilt dependency injection
- UseCases for business logic
- Repository pattern for data/player/preferences access
- StateFlow for UI state
- SharedFlow/Channel for one-time events

## Main Architecture Flow

Activity / Fragment
→ ViewModel
→ UseCase
→ Repository
→ DataSource / Player / Preferences / Database / Legacy API

## Core Rules

- Activity and Fragment should only handle UI work:
    - view binding
    - lifecycle
    - click listeners
    - rendering state
    - navigation
    - collecting flows

- Do not put business logic in Activity or Fragment.
- ViewModel should expose immutable UI state.
- ViewModel should call UseCases, not directly access data sources.
- UseCases should contain one clear app action.
- Repositories should hide data/player/preference implementation details.
- Use Hilt constructor injection.
- Preserve existing behavior when refactoring.
- Give complete code for every changed or new file.