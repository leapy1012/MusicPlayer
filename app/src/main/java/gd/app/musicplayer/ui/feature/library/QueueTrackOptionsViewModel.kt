package gd.app.musicplayer.ui.feature.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.repo.PlaybackQueueRepo
import gd.app.musicplayer.domain.usecase.playback.ClearQueueUseCase
import gd.app.musicplayer.domain.usecase.playback.ObservePlaybackStateUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.ReplaceQueueUseCase
import gd.app.musicplayer.domain.usecase.track.DeleteTracksUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface QueueTrackOptionsEvent {
    data object Dismiss : QueueTrackOptionsEvent
    data class ShowToast(val messageRes: Int) : QueueTrackOptionsEvent
    data class OpenAlbum(val album: MusicSet.Album) : QueueTrackOptionsEvent
    data class OpenArtist(val artist: MusicSet.Artist) : QueueTrackOptionsEvent
    data class OpenAddTo(val tracks: List<Music>) : QueueTrackOptionsEvent
    data class Share(val tracks: List<Music>) : QueueTrackOptionsEvent
}

@HiltViewModel
class QueueTrackOptionsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val playTracksUseCase: PlayTracksUseCase,
    private val deleteTracksUseCase: DeleteTracksUseCase,
    private val observePlaybackStateUseCase: ObservePlaybackStateUseCase,
    private val clearQueueUseCase: ClearQueueUseCase,
    private val replaceQueueUseCase: ReplaceQueueUseCase,
    private val playbackQueueRepo: PlaybackQueueRepo
) : ViewModel() {

    private val _events = MutableSharedFlow<QueueTrackOptionsEvent>()
    val events: SharedFlow<QueueTrackOptionsEvent> = _events.asSharedFlow()

    fun onPlay(music: Music) {
        playTracksUseCase(appContext, listOf(music), 0)
        emit(QueueTrackOptionsEvent.Dismiss)
    }

    fun onAddToPlaylist(music: Music) {
        emit(QueueTrackOptionsEvent.OpenAddTo(listOf(music)))
        emit(QueueTrackOptionsEvent.Dismiss)
    }

    fun onOpenAlbum(music: Music) {
        val albumName = music.album.takeIf { it.isNotBlank() } ?: return
        emit(
            QueueTrackOptionsEvent.OpenAlbum(
                MusicSet.Album(
                    id = music.albumId.toLongOrNull() ?: MusicSet.ALBUMS,
                    name = albumName,
                    albumArt = music.albumPicture,
                    artist = music.artist,
                    musicCount = 0,
                    date = music.date ?: 0L
                )
            )
        )
        emit(QueueTrackOptionsEvent.Dismiss)
    }

    fun onOpenArtist(music: Music) {
        val artistName = music.artist.takeIf { it.isNotBlank() } ?: return
        emit(
            QueueTrackOptionsEvent.OpenArtist(
                MusicSet.Artist(
                    id = MusicSet.ARTISTS,
                    name = artistName,
                    musicCount = 0,
                    albumCount = 0,
                    albumArt = music.albumPicture
                )
            )
        )
        emit(QueueTrackOptionsEvent.Dismiss)
    }

    fun onShare(music: Music) {
        emit(QueueTrackOptionsEvent.Share(listOf(music)))
        emit(QueueTrackOptionsEvent.Dismiss)
    }

    fun onRemoveFromQueue(music: Music) {
        viewModelScope.launch {
            val queue = playbackQueueRepo.getQueue()
            val state = observePlaybackStateUseCase().value
            val index = queue.indexOfFirst { it.id == music.id }
            if (index < 0) return@launch

            val newQueue = queue.toMutableList().apply { removeAt(index) }
            if (newQueue.isEmpty()) {
                clearQueueUseCase(appContext)
                emit(QueueTrackOptionsEvent.ShowToast(R.string.succeed))
                emit(QueueTrackOptionsEvent.Dismiss)
                return@launch
            }

            val newIndex = when {
                index < state.currentIndex -> state.currentIndex - 1
                state.currentIndex >= newQueue.size -> newQueue.lastIndex
                else -> state.currentIndex
            }
            replaceQueueUseCase(appContext, newQueue, newIndex)
            emit(QueueTrackOptionsEvent.ShowToast(R.string.succeed))
            emit(QueueTrackOptionsEvent.Dismiss)
        }
    }

    fun onDeleteConfirmed(music: Music) {
        viewModelScope.launch {
            val deletedCount = deleteTracksUseCase(listOf(music))
            emit(
                QueueTrackOptionsEvent.ShowToast(
                    if (deletedCount > 0) R.string.succeed else R.string.feature_not_implemented
                )
            )
            emit(QueueTrackOptionsEvent.Dismiss)
        }
    }

    private fun emit(event: QueueTrackOptionsEvent) {
        viewModelScope.launch { _events.emit(event) }
    }
}

