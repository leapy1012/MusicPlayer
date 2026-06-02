# Playback Refactor Plan

## Current Diagnosis

The playback package already contains many useful extractions, but the main boundary is still `MusicPlaybackService`.
Most extracted files are extension functions over the service, so they share the same mutable fields and lifecycle state.
This makes the code look modular while still behaving like one large class.

Main issues:

- `MusicPlaybackService` owns Android lifecycle, dependency wiring, ExoPlayer instances, queue state, Media3 session, notifications, overlays, restore, persistence, audio focus, effects, progress ticking, and shutdown.
- Service extension files move code out of the class body but keep tight coupling through `internal lateinit var` fields and `OrNull()` guards.
- Callback adapter classes often delegate back to `MusicPlaybackService`, so ownership is not clear.
- Runtime state is spread across several mutable state holders, which makes ordering bugs hard to reason about.
- Notification, widget, artwork, queue persistence, and playback state publishing are triggered from many places.

## Target Shape

Keep `MusicPlaybackService` as a thin Android boundary:

- Receive Android lifecycle and Media3 callbacks.
- Own foreground-service compliance.
- Delegate all playback behavior to a single `PlaybackSession`.
- Expose no mutable playback internals to package-level extension files.

Recommended core objects:

- `PlaybackSession`: top-level lifecycle owner for one service session.
- `PlaybackEngineController`: ExoPlayer, crossfade player, audio focus, play, pause, seek, next, previous.
- `PlaybackQueueController`: queue mutation and current-index rules.
- `PlaybackStateStore`: immutable runtime state as `StateFlow<PlaybackUiState>`.
- `PlaybackPersistenceController`: queue/progress/session persistence policy.
- `PlaybackPresentationController`: notification, Media3 metadata, widget, artwork, command buttons.
- `PlaybackFeatureController`: optional features such as lyrics overlays, lock screen, effects, sleep timer.

## Refactor Order

1. Introduce `PlaybackSession`

Move the service runtime fields into a constructor-created object. The service should hold only:

```kotlin
@AndroidEntryPoint
class MusicPlaybackService : MediaSessionService() {
    @Inject lateinit var sessionFactory: PlaybackSessionFactory

    private var session: PlaybackSession? = null

    override fun onCreate() {
        super.onCreate()
        session = sessionFactory.create(service = this).also { it.start() }
    }

    override fun onDestroy() {
        session?.stop()
        session = null
        super.onDestroy()
    }
}
```

This removes the need for most `lateinit` guards.

2. Collapse service extension files into real owners

Do not keep adding `MusicPlaybackServiceXxxOps.kt`. Move behavior by responsibility:

- `MusicPlaybackServicePlayerOps.kt` -> `PlaybackEngineController`.
- `MusicPlaybackServiceQueueOps.kt` -> `PlaybackQueueController` plus command facade.
- `MusicPlaybackServiceStateOps.kt` -> `PlaybackStateStore` and `PlaybackPersistenceController`.
- `MusicPlaybackServiceObservers.kt` -> `PlaybackPreferenceObserver`.
- `MusicPlaybackServiceRuntimeBridge.kt` -> `PlaybackSession` lifecycle methods.
- `MusicPlaybackServiceSetup.kt` -> `PlaybackSessionFactory`.

3. Replace callback fan-out with narrow interfaces

Callbacks should describe the dependency needed, not the service that happens to provide it.

Prefer:

```kotlin
interface PlaybackActions {
    suspend fun play()
    suspend fun pause()
    suspend fun seekTo(positionMs: Long)
    suspend fun playNext()
}
```

Avoid:

```kotlin
class PlaybackCommandCallbacks(
    private val service: MusicPlaybackService
)
```

4. Centralize state publishing

Create one event reducer for playback state:

```kotlin
sealed interface PlaybackEvent {
    data object Started : PlaybackEvent
    data object Paused : PlaybackEvent
    data class QueueChanged(val queue: List<Music>, val index: Int) : PlaybackEvent
    data class PositionChanged(val positionMs: Long) : PlaybackEvent
}
```

Only the reducer updates runtime state, notification state, widgets, and Media3 metadata.
This prevents duplicated calls such as update queue, refresh artwork, persist snapshot, publish state, and update notification.

5. Make mutable runtime state explicit

Replace scattered mutable holders with immutable snapshots:

```kotlin
data class PlaybackRuntime(
    val queue: List<Music> = emptyList(),
    val currentIndex: Int = -1,
    val positionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val isRestored: Boolean = false,
    val stopAfterCurrent: Boolean = false
)
```

Use `MutableStateFlow<PlaybackRuntime>` inside one owner and expose `StateFlow<PlaybackRuntime>`.

6. Keep package count lower by grouping small files

Do not create a file for every tiny state object. Group by feature boundary:

- `service`: `MusicPlaybackService`, `PlaybackSession`, `PlaybackSessionFactory`.
- `engine`: player factory, player controller, transition/crossfade, audio focus.
- `queue`: queue state, queue manager, queue commands.
- `presentation`: notification, Media3 bridge, widget, artwork.
- `persistence`: restore, snapshot, persistence policy.
- `features`: lyrics overlays, headset automation, sleep timer, effects.

## First Safe Implementation Step

Start by creating `PlaybackSession` and moving only lifecycle-owned fields:

- `serviceScope`
- `player`
- `crossfadePlayer`
- `media3Session`
- `progressTicker`
- release logic

Do not change queue behavior in the first step. The first PR should only change ownership and lifecycle cleanup.

Success criteria:

- `MusicPlaybackService` has fewer than 10 fields.
- No package-level extension function has receiver `MusicPlaybackService`.
- Command handling depends on `PlaybackActions`, not the service.
- Notification/widget/artwork updates are triggered by one event pipeline.
- Queue operations are testable without Android `Service`.

