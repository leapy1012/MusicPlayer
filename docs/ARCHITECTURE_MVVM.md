# Modern Android MVVM Architecture

## Goal

This project should converge on a feature-first MVVM architecture with explicit data and domain boundaries:

```text
app/src/main/java/gd/app/musicplayer/
  app/
    MusicPlayerApp.kt
    AppContainer.kt

  core/
    extension/
    util/
    navigation/
    permission/
    ui/
      drawable/

  data/
    db/
      dao/
      entity/
      migration/
      seed/
    model/
    repo/

  domain/
    model/
    usecase/

  ui/
    shell/
    common/
      adapter/
      base/
      decoration/
      dialog/
      menu/
      view/
      viewholder/
    feature/
      drivemode/
      equalizer/
      hidden/
      library/
      playlist/
      scan/
      search/
      selection/
      setting/
      sleep/
      theme/
      widget/
```

## Layer Rules

- `ui/*` renders state and forwards user actions.
- `domain/usecase/*` owns business actions that combine repositories or non-trivial rules.
- `data/repo/*` hides Room, MediaStore, preferences, and file operations behind stable APIs.
- `data/model/*` contains persistence-facing models only.
- `core/*` contains generic platform helpers with no feature knowledge.

## ViewModel Rules

- Every screen owns a single `UiState` stream exposed as `StateFlow`.
- One-shot events should be modeled separately from persistent state.
- ViewModels should not call Android UI APIs directly.
- Sorting, filtering, and mutation entry points should live in ViewModels or use cases, not adapters.

## UI Rules

- Adapters are rendering and interaction bridges only.
- Fragments and activities should delegate mutations to ViewModels.
- Shared widgets, popup controllers, and themed containers belong in `ui/common`.
- Feature-specific menus and dialogs stay inside their feature package unless reused by 2+ features.

## Data Rules

- DAO interfaces should expose small, composable queries and transactional mutation helpers.
- Repositories should be the only layer that knows which DAO implements which capability.
- File deletion, MediaStore deletion, playlist writes, and preference updates should not be duplicated across screens.

## Current High-Value Gaps

- `MusicSet` still contains UI-specific helpers such as placeholder and artwork logic.
- Some mutation flows still call DAOs directly from UI classes.
- A few popup and dialog utilities still carry partially migrated behavior from the obfuscated codebase.
- Domain use cases are still thin or missing for track actions, playlist mutations, and theme editing.

## Recommended Migration Order

1. Extract track actions into `domain/usecase`:
   `PlayTracksUseCase`, `EnqueueTracksUseCase`, `DeleteTracksUseCase`, `AddTracksToPlaylistUseCase`.
2. Move direct DAO calls out of fragments and activities into repositories or use cases.
3. Replace ad hoc toolbar/menu callbacks with feature-level action handlers that return typed intents/events.
4. Move `MusicSet` UI helpers into `ui/common/model` or dedicated mapper classes.
5. Standardize every feature on:
   `UiState`, `UiAction`, `UiEvent`, `ViewModel`, `Fragment/Activity`, `Adapter`.

## Practical Standard For This Repo

- New features should be implemented in the target shape immediately.
- Existing features should only be migrated when touched for behavior work.
- Large rewrites should be avoided unless they remove duplicated logic or unblock missing behavior.
