# MusicPlayer MVVM Project Structure

This project is being organized around a modern Android MVVM architecture.

## Target package layout

```text
gd.app.musicplayer
  core/       shared infrastructure and cross-cutting utilities
  data/       local data sources and repository implementations
  domain/     pure Kotlin models, repository interfaces, and use cases
  feature/    screen-specific UI, ViewModels, state, and actions
  playback/   Media3/service/media-session integration
```

## Dependency direction

```text
feature -> domain <- data
feature -> core
data    -> core
playback -> domain/core
```

`domain` is the center of the app. It should stay free of Android framework dependencies.

## Migration rules

1. Move screen packages under `feature/<feature-name>`.
2. Keep Activities/Fragments thin. They should render UI state and delegate actions to ViewModels.
3. Put business logic in use cases under `domain/usecase`.
4. Put repository interfaces in `domain/repository`.
5. Put concrete repository implementations in `data/repository`.
6. Use Hilt constructor injection instead of manual dependency lookup.
7. Migrate one feature at a time to `ViewModel + UiState + Action + Event`.

## Recommended next migration order

1. Search
2. Library
3. Playlist
4. Player queue
5. Playback service/controller
6. Settings/theme

This restructuring is package-level only. It is designed to be safe before deeper behavior refactors.
