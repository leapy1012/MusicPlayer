package gd.app.musicplayer.playback.favorite

import gd.app.musicplayer.core.common.dispatcher.AppDispatchers
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.repository.PlaylistRepo
import gd.app.musicplayer.domain.usecase.playlist.ToggleFavoriteTrackUseCase
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
import kotlinx.coroutines.withContext

class CurrentFavoriteController(
    private val playlistRepo: PlaylistRepo,
    private val toggleFavoriteTrackUseCase: ToggleFavoriteTrackUseCase,
    private val queueManager: PlaybackQueueManager,
    private val runtimeStateStore: PlaybackRuntimeStateStore,
    private val dispatchers: AppDispatchers,
    private val callbacks: Callbacks
) {

    interface Callbacks {
        fun onFavoriteChanged()
    }

    private var observerJob: Job? = null

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
                    playlistRepo.observeIsFavorite(musicId)
                        .distinctUntilChanged()
                        .map { isFavorite ->
                            musicId to isFavorite
                        }
                }
            }
            .onEach { (musicId, isFavorite) ->
                syncFavoriteStateForTrack(
                    trackId = musicId,
                    isFavorite = isFavorite,
                    persistQueue = false
                )
            }
            .launchIn(scope)
    }

    fun stopObserving() {
        observerJob?.cancel()
        observerJob = null
    }

    suspend fun toggleCurrent(): Boolean {
        val music = queueManager.currentTrack ?: return false
        return toggleFavoriteInternal(music)
    }

    suspend fun setCurrentFavorite(isFavorite: Boolean): Boolean {
        val music = queueManager.currentTrack ?: return false

        val currentFavorite = withContext(dispatchers.io) {
            playlistRepo.isFavorite(music.id)
        }

        return if (currentFavorite != isFavorite) {
            toggleFavoriteInternal(music)
        } else {
            syncFavoriteStateForTrack(
                trackId = music.id,
                isFavorite = isFavorite,
                persistQueue = false
            )

            isFavorite
        }
    }

    private suspend fun toggleFavoriteInternal(music: Music): Boolean {
        val isFavorite = withContext(dispatchers.io) {
            toggleFavoriteTrackUseCase(music.id)
        }

        syncFavoriteStateForTrack(
            trackId = music.id,
            isFavorite = isFavorite,
            persistQueue = true
        )

        return isFavorite
    }

    private fun syncFavoriteStateForTrack(
        trackId: Long,
        isFavorite: Boolean,
        persistQueue: Boolean
    ) {
        val changed = queueManager.updateFavoriteState(
            trackId = trackId,
            isFavorite = isFavorite
        )

        if (!changed) return

        if (persistQueue) {
            queueManager.save()
        }

        callbacks.onFavoriteChanged()
    }
}