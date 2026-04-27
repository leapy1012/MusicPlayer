# MVVM Architecture Migration

This document defines the target architecture for migrating MusicPlayer to a modern Android MVVM structure.

## Target package layout

```text
app/src/main/java/gd/app/musicplayer/
  core/
    common/
    di/
    dispatchers/
    media/
    permission/
    theme/
    ui/

  data/
    local/
      dao/
      db/
      entity/
    media/
      metadata/
      scanner/
    repository/

  domain/
    model/
    repository/
    usecase/

  playback/
    controller/
    notification/
    service/

  feature/
    library/
    player/
    playlist/
    search/
    scan/
    settings/
    widget/
```

## Dependency direction

```text
feature -> domain -> data
playback -> domain
core -> shared by all layers
```

The `domain` layer should stay pure Kotlin and should not depend on Android framework classes.

## Layer responsibilities

### core

App-wide utilities and infrastructure such as Hilt modules, dispatchers, permissions, theme primitives, common UI helpers, and constants.

### data

Room database code, MediaStore/file scanners, metadata readers, preferences, and concrete repository implementations.

### domain

Pure models, repository interfaces, and use cases. This layer defines what the app can do without knowing how Android stores or displays it.

### playback

Foreground service, Media3 integration, notification controls, media button handling, queue/session controller, and adapters between Android media APIs and domain use cases.

### feature

User-facing screens grouped by product area. Each feature owns its Activity/Fragment, ViewModel, UI state, UI actions, adapters, and feature-only UI helpers.

## Feature MVVM pattern

Each migrated screen should follow this shape:

