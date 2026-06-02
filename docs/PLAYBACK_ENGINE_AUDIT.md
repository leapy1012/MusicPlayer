# Playback Engine Audit

## Scope
- Service entry/lifecycle: `MusicPlaybackService`, `PlaybackLifecycleController`, `PlaybackServiceRuntime`.
- Command path: `PlaybackCommandHandler`, `PlaybackCommandCallbacks`.
- Queue/player path: `QueueActionController`, `PlayerQueueController`, `PlaybackQueueManager`, `MusicPlaybackService*Ops`.
- State/notification/widget path: `PlaybackStateUpdateCoordinator`, `PlaybackStateOrchestrator`, `PlaybackStatePublisher`, `MusicPlaybackServiceStateOps`.

## High-Risk Findings

1. Blocking calls on service paths (`runBlocking`) can stall binder/main-sensitive flows.
- File: `app/src/main/java/gd/app/musicplayer/playback/service/MusicPlaybackServiceStateOps.kt`
- Evidence: blocking paths at `updateWidgetSnapshotBlocking`, `persistPlaybackSnapshotBlocking`, `persistCurrentTrackProgressBlocking`, persisted-session clear helpers.
- Risk: service responsiveness regressions and ANR pressure when I/O backs up.

2. Duplicated state publish layers increase complexity and drift risk.
- Files:
  - `PlaybackStateUpdateCoordinator.kt`
  - `PlaybackStateOrchestrator.kt`
  - `MusicPlaybackServiceStateOps.kt`
  - `MusicPlaybackServiceMiscOps.kt`
- Evidence: each layer exposes similarly named methods (`publishAllRuntimeState`, `publishPlaybackState`, `updateNotification`, `publishStateAfterShutdown`) and forwards with policy variations.
- Risk: behavior differences across call sites; hard-to-reason notification/widget consistency.

3. Service still owns excessive mutable runtime state and lifecycle guards.
- File: `MusicPlaybackService.kt`
- Evidence: dozens of mutable fields and 30+ `isXInitialized()` guard helpers.
- Risk: high coupling and implicit ordering requirements; difficult safe refactoring.

## Medium-Risk Findings

4. Queue command wrappers repeat the same coroutine + restore + guard template.
- File: `MusicPlaybackServiceQueueOps.kt`
- Evidence: repeated `serviceScope.launch { ensurePlaybackRestored(); if (!isQueueActionControllerInitialized()) return@launch; ... }`.
- Risk: boilerplate, uneven error handling, future divergence.

5. Widget snapshot conversion is duplicated with inconsistent play mode filling.
- File: `MusicPlaybackServiceStateOps.kt`
- Evidence: service-local converters use `latestSettingPreferences.playMode` while extension converters set `playMode = 0`.
- Risk: accidental wrong widget mode state depending on call path.

6. Notification update can be triggered from many independent paths.
- Files:
  - `MusicPlaybackServiceObservers.kt`
  - `MusicPlaybackServicePlayerOps.kt`
  - `MusicPlaybackServiceQueueOps.kt`
  - `PlaybackStateOrchestrator.kt`
- Risk: bursty/duplicated updates and extra UI churn.

## Step-by-Step Refactor Plan

1. Baseline & safety
- Add a playback regression script/checklist (manual + instrumentation where possible).
- Cover: empty start, play/pause, next/prev, seek, queue mutations, process restore, task removed, media buttons, sleep timer, notification close, widget sync.

2. Canonical transition API
- Add one transition entrypoint (e.g., `PlaybackTransitionDispatcher`) that owns side effects for:
  - runtime state publish
  - notification update
  - widget update
  - persistence trigger
- Move policy out of ad hoc call sequences.

3. Remove blocking service operations
- Replace `runBlocking` service paths with suspend/non-blocking versions.
- Keep one explicit emergency blocking path only if Android contract forces sync behavior.

4. Collapse publish/update duplication
- Keep one abstraction layer (`PlaybackStateUpdateCoordinator`) and slim `MusicPlaybackService*Ops` helpers to passthrough-free call sites.
- Merge duplicated method names and eliminate redundant wrappers.

5. Normalize widget snapshot building
- Keep one mapper for `WidgetPlaybackSnapshot`.
- Inject `playMode` explicitly (no implicit `0` fallback in extension conversion).

6. Reduce service mutable surface
- Move command operation state into dedicated runtime state holder objects.
- Replace broad `isXInitialized()` exposure with initialization ordering guarantees and nullable collaborators where appropriate.

7. Consolidate queue command wrappers
- Introduce helper: `launchAfterRestore { queueActionController.<op>() }`.
- Keep logging/error policy centralized.

8. Notification throttling/coalescing
- Coalesce fast successive updates (player callbacks + preference observers).
- Preserve immediate update only for foreground/transport-critical events.

9. Persistence policy cleanup
- Define explicit persistence matrix by event:
  - seek/pause/stop/track transition/shutdown/queue mutation.
- Remove ambiguous no-op placeholders and scattered writes.

10. Tests for invariants
- Add tests around:
  - queue/index remap behavior
  - media transition correction logic
  - publish-throttling policy
  - restore+shutdown state correctness

## Suggested Execution Slices

- Slice A (low risk): queue wrapper dedup + widget snapshot mapper unification.
- Slice B (medium): publish/update pipeline collapse.
- Slice C (medium/high): non-blocking persistence + blocking-path removal.
- Slice D (high): service state surface reduction + ownership migration.

