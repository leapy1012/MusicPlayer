# Project Structure

This project follows a layered MVVM architecture:

`Activity/Fragment -> ViewModel -> UseCase -> Repository -> DataSource/Player/Preferences/DB`

## Package Layout

Use this structure for all new code and refactors:

- `gd.app.musicplayer.app`
  - App bootstrap, Hilt setup, DI modules.
- `gd.app.musicplayer.core`
  - Shared framework-agnostic utilities, UI primitives, extensions, theming base helpers.
- `gd.app.musicplayer.data`
  - Repositories, DAO access, entities/models, data sources, preference gateways.
- `gd.app.musicplayer.domain`
  - Business actions as single-purpose UseCases, domain-only contracts/models.
- `gd.app.musicplayer.playback`
  - Playback engine, service, media session, queue/runtime controllers.
- `gd.app.musicplayer.ui`
  - Screens, fragments/activities, adapters, UI-only mappers and contracts.
- `gd.app.musicplayer.util`
  - Legacy helpers pending migration to `core`/`data`.

## Placement Rules

- Keep `UseCase` classes in `domain/usecase/<feature>`.
- Keep ViewModels in `ui/...` and inject UseCases only.
- Do not place UseCases under `ui`.
- UI contracts/events can stay in `ui`, but request/result models shared with business logic should live in `domain`.
- Repositories must be injected into UseCases, not into Activities/Fragments.
- One-time UI events must use `SharedFlow`/`Channel`; persistent state must use `StateFlow`.

## Recent Refactor Applied

- Selection flow UseCases moved from:
  - `ui/feature/selection/MusicSelectUseCases.kt`
- To:
  - `domain/usecase/selection/LoadMusicSelectDataUseCase.kt`
  - `domain/usecase/selection/ConfirmMusicSelectUseCase.kt`
  - `domain/usecase/selection/MusicSelectModels.kt`

This keeps UI thin and aligns with AGENTS.md architecture requirements.

- Playlist backup manager moved from UI to data layer:
  - `ui/feature/playlist/PlaylistBackupManager.kt` -> `data/backup/PlaylistBackupManager.kt`
