package gd.app.musicplayer.playback.artwork

import android.graphics.Bitmap
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.usecase.library.ObserveAlbumPictureUseCase
import gd.app.musicplayer.playback.ArtworkLoader
import gd.app.musicplayer.playback.NotificationMediaSessionBridge
import gd.app.musicplayer.playback.PlaybackRuntimeStateStore
import gd.app.musicplayer.playback.queue.PlaybackQueueManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class CurrentArtworkController(
    private val defaultArtwork: Bitmap,
    private val artworkLoader: ArtworkLoader,
    private val observeAlbumPictureUseCase: ObserveAlbumPictureUseCase,
    private val queueManager: PlaybackQueueManager,
    private val runtimeStateStore: PlaybackRuntimeStateStore,
    private val notificationSessionBridge: NotificationMediaSessionBridge,
    private val callbacks: Callbacks
) {

    interface Callbacks {
        fun onQueueArtworkChanged()
        fun onArtworkLoaded()
        fun onArtworkCleared()
    }

    private var observerJob: Job? = null

    var currentArtwork: Bitmap? = null
        private set

    var currentArtworkTrackId: Long = NO_TRACK_ID
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(scope: CoroutineScope) {
        observerJob?.cancel()

        observerJob = runtimeStateStore.state
            .map { state ->
                state.currentTrack?.id
            }
            .distinctUntilChanged()
            .flatMapLatest { musicId: Long? ->
                if (musicId == null) {
                    emptyFlow()
                } else {
                    observeAlbumPictureUseCase(musicId)
                        .distinctUntilChanged()
                        .map { artworkPath ->
                            musicId to artworkPath
                        }
                }
            }
            .onEach { (musicId, artworkPath) ->
                syncCurrentTrackArtwork(
                    trackId = musicId,
                    artworkPath = artworkPath
                )
            }
            .launchIn(scope)
    }

    fun stopObserving() {
        observerJob?.cancel()
        observerJob = null
    }

    fun refresh(force: Boolean = false) {
        val music = queueManager.currentTrack

        if (music == null) {
            currentArtwork = null
            currentArtworkTrackId = NO_TRACK_ID

            notificationSessionBridge.clearMetadata()
            callbacks.onArtworkCleared()
            return
        }

        if (!force && currentArtworkTrackId == music.id) {
            notificationSessionBridge.updateMetadata(
                music = music,
                artwork = currentArtwork ?: defaultArtwork
            )

            callbacks.onArtworkLoaded()
            return
        }

        val requestedTrackId = music.id

        artworkLoader.load(
            music = music,
            onLoaded = { bitmap ->
                if (queueManager.currentTrack?.id != requestedTrackId) {
                    return@load
                }

                handleArtworkLoaded(
                    music = music,
                    bitmap = bitmap
                )
            },
            onFailed = { bitmap ->
                if (queueManager.currentTrack?.id != requestedTrackId) {
                    return@load
                }

                handleArtworkLoaded(
                    music = music,
                    bitmap = bitmap
                )
            },
            onCleared = {
                if (currentArtworkTrackId == requestedTrackId) {
                    currentArtwork = defaultArtwork
                }
            }
        )
    }

    fun clear() {
        artworkLoader.clear()
        currentArtwork = null
        currentArtworkTrackId = NO_TRACK_ID
    }

    private fun syncCurrentTrackArtwork(
        trackId: Long,
        artworkPath: String?
    ) {
        val currentTrack = queueManager.currentTrack ?: return

        if (
            currentTrack.id != trackId ||
            currentTrack.albumPicture == artworkPath
        ) {
            return
        }

        val changed = queueManager.updateArtworkPath(
            trackId = trackId,
            artworkPath = artworkPath
        )

        if (!changed) return

        queueManager.save()
        refresh(force = true)
        callbacks.onQueueArtworkChanged()
    }

    private fun handleArtworkLoaded(
        music: Music,
        bitmap: Bitmap
    ) {
        if (queueManager.currentTrack?.id != music.id) return

        currentArtwork = bitmap
        currentArtworkTrackId = music.id

        notificationSessionBridge.updateMetadata(
            music = music,
            artwork = bitmap
        )

        callbacks.onArtworkLoaded()
    }

    private companion object {
        private const val NO_TRACK_ID = Long.MIN_VALUE
    }
}