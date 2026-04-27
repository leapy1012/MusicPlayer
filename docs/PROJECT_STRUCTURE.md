# Project Structure

## Target Shape

```text
app/src/main/java/gd/app/musicplayer/
  app/
    MusicPlayerApp.kt
    AppContainer.kt

  core/
    extension/
    navigation/
    permission/
    util/
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
      MainActivity.kt
      MoreFragment.kt
      player/
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
    common/
      adapter/
      base/
      decoration/
      dialog/
      menu/
      view/
      viewholder/
```

## Current Migration Rules

- `ui/shell`: app shell only, including drawer and shell-only fragments.
- `ui/feature/*`: one feature per package, with its activity/fragment/viewmodel/adapter kept together.
- `ui/common/*`: reusable UI pieces shared across multiple features.
- `core/*`: generic helpers that do not belong to a single feature.
- `data/*`: database, repositories, and persistence-facing models.
- `domain/*`: use cases and business-level models once they diverge from persistence models.

## Applied In This Pass

- Moved shell coordination into `ui.shell`.
- Moved top-level single-screen activities into `ui.feature.*`.
- Split generic helpers out of `util` into:
  - `core.extension`
  - `core.util`
  - `core.ui.drawable`
- Cleaned panel package naming away from obfuscation.

## Next Safe Moves

1. Move `ui/base`, `ui/view`, `ui/menu`, and `ui/viewholder` into `ui/common/*`.
2. Finish removing stale XML references that still point at old `gd.app.musicplayer.ui.view.*` tags.
3. Move feature-specific adapters/view holders into their owning feature packages.
4. Reduce `data.model.MusicSet` UI knowledge by pushing UI-only helpers into `ui/common`.
