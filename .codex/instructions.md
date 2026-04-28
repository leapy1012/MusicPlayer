# MusicPlayer Refactor Instructions

This is an Android music player app.

Refactor the project into a modern Android MVVM architecture using:
- Kotlin
- Hilt
- ViewModel
- StateFlow
- Coroutines
- Repository pattern
- Use cases where useful
- XML/ViewBinding UI, not Compose

Do not rewrite the whole app at once.
Preserve existing behavior.
Keep the project compiling after every stage.
Do not remove features.
Do not introduce unnecessary multi-module architecture.

Target architecture:
- app/di for Hilt modules
- core/ for shared utilities, dispatchers, base UI, extensions
- data/ for repositories, media store, database, preferences
- domain/ for models, repository interfaces, use cases
- ui/feature/ for Activities, Fragments, ViewModels, UiState, UiEvent
- service/ for playback service, media session, notifications

Activities and Fragments should only:
- render UI state
- collect ViewModel state
- forward events to ViewModel
- handle navigation/effects

Business logic must move into ViewModels, use cases, repositories, or data sources.