# Playback Regression Checklist

## Startup and Restore
- Start app with empty process and verify service restore behavior.
- Verify empty queue + `PLAY` action loads default playable queue.
- Verify restored queue/index/position after process death.
- Verify no stuck "restoring" state when restore fails/empty.

## Core Controls
- `PLAY` / `PAUSE` / `TOGGLE` from UI.
- `NEXT` / `PREVIOUS` from UI and media buttons.
- `SEEK TO` from player UI and notification/Media3 transport.
- `RESTART CURRENT` behavior.

## Queue Operations
- Play from queue at index.
- Enqueue items into non-empty queue.
- Play-next insertion behavior.
- Replace queue and index remap behavior.
- Remove queue item (including current item).
- Move queue item and current index correctness.
- Clear queue keeping notification.

## Notification and Media Session
- Foreground promotion on playback commands.
- Notification close action pauses and persists snapshot.
- Favorite toggle action from notification and Media3 custom command.
- Media3 stop command shutdown path.
- Notification style refresh action.

## Playback Modes and Transitions
- Cycle playback mode and verify next/previous resolution.
- Verify auto-transition at queue end for each mode.
- Verify crossfade commit transition and suppression flag behavior.

## Audio and Effects
- Audio focus request/abandon behavior on play/pause/stop.
- Play/pause fade behavior enabled/disabled.
- Sound effect preferences apply on updates.

## Overlays and Widgets
- Desktop lyric overlay reacts to playback state and preference changes.
- Status-bar lyric overlay reacts to playback state and preference changes.
- Widget snapshot updates on queue/playback/favorite/artwork changes.

## Shutdown and Lifecycle
- `StopInPlace`, `StopWithoutClearingQueue`, `StopAndClearQueue`, `ExitService`.
- Task removed while playing vs paused.
- Screen off receiver register/unregister lifecycle.
- Ensure grouped state resets on destroy:
  - `jobState`
  - `startupState`
  - `media3TransportState`
  - `sessionFlags`
  - `runtimeCacheState`
