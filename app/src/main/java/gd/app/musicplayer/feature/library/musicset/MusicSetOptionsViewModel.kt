package gd.app.musicplayer.feature.library.musicset

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.R
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.usecase.playback.EnqueueTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayNextTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayTracksUseCase
import gd.app.musicplayer.domain.usecase.hidden.HideSelectionUseCase
import gd.app.musicplayer.domain.usecase.library.ObserveTracksUseCase
import gd.app.musicplayer.domain.usecase.playlist.DeletePlaylistUseCase
import gd.app.musicplayer.domain.usecase.track.DeleteTracksUseCase
import gd.app.musicplayer.domain.usecase.track.DeleteTracksFromLibraryUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MusicSetOptionsEvent {
    data object Dismiss : MusicSetOptionsEvent
    data class ShowToast(val messageRes: Int, val args: List<Any> = emptyList()) : MusicSetOptionsEvent
    data class OpenAddTo(val tracks: List<Music>) : MusicSetOptionsEvent
    data class ShareTracks(val tracks: List<Music>) : MusicSetOptionsEvent
}

@HiltViewModel
class MusicSetOptionsViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val playTracksUseCase: PlayTracksUseCase,
    private val playNextTracksUseCase: PlayNextTracksUseCase,
    private val enqueueTracksUseCase: EnqueueTracksUseCase,
    private val deleteTracksUseCase: DeleteTracksUseCase,
    private val deleteTracksFromLibraryUseCase: DeleteTracksFromLibraryUseCase,
    private val hideSelectionUseCase: HideSelectionUseCase,
    private val deletePlaylistUseCase: DeletePlaylistUseCase,
    private val observeTracksUseCase: ObserveTracksUseCase
) : ViewModel() {

    private val _events = MutableSharedFlow<MusicSetOptionsEvent>()
    val events: SharedFlow<MusicSetOptionsEvent> = _events.asSharedFlow()

    fun onTrackAction(action: Int, musicSet: MusicSet) {
        viewModelScope.launch {
            val tracks = resolveTracks(musicSet)
            if (tracks.isEmpty()) {
                _events.emit(MusicSetOptionsEvent.ShowToast(R.string.list_is_empty))
                return@launch
            }

            when (action) {
                R.string.operation_play -> playTracksUseCase(tracks, 0)
                R.string.play_next_2 -> {
                    playNextTracksUseCase(tracks)
                    _events.emit(
                        MusicSetOptionsEvent.ShowToast(
                            messageRes = R.string.enqueue_msg_count,
                            args = listOf(tracks.size)
                        )
                    )
                }

                R.string.operation_enqueue -> {
                    enqueueTracksUseCase(tracks)
                    _events.emit(
                        MusicSetOptionsEvent.ShowToast(
                            messageRes = R.string.enqueue_msg_count,
                            args = listOf(tracks.size)
                        )
                    )
                }

                R.string.add_to -> _events.emit(MusicSetOptionsEvent.OpenAddTo(tracks))
                R.string.share -> _events.emit(MusicSetOptionsEvent.ShareTracks(tracks))
            }

            _events.emit(MusicSetOptionsEvent.Dismiss)
        }
    }

    fun hideFolder(folder: MusicSet.Folder) {
        viewModelScope.launch {
            hideSelectionUseCase(folderPaths = listOf(folder.folderPath), songIds = emptyList())
            _events.emit(MusicSetOptionsEvent.ShowToast(R.string.hidden_folders_tips))
            _events.emit(MusicSetOptionsEvent.Dismiss)
        }
    }

    fun deleteSet(musicSet: MusicSet, deleteSourceFile: Boolean) {
        viewModelScope.launch {
            when (musicSet) {
                is MusicSet.Playlist -> {
                    deletePlaylistUseCase(musicSet.id)
                    _events.emit(MusicSetOptionsEvent.ShowToast(R.string.succeed))
                }

                else -> {
                    val tracks = resolveTracks(musicSet)
                    if (tracks.isEmpty()) {
                        _events.emit(MusicSetOptionsEvent.ShowToast(R.string.list_is_empty))
                        return@launch
                    }
                    val deletedCount = if (deleteSourceFile) {
                        deleteTracksUseCase(tracks)
                    } else {
                        deleteTracksFromLibraryUseCase(tracks.map { it.id })
                        tracks.size
                    }
                    _events.emit(
                        MusicSetOptionsEvent.ShowToast(
                            if (deletedCount > 0) R.string.succeed else R.string.feature_not_implemented
                        )
                    )
                }
            }
            _events.emit(MusicSetOptionsEvent.Dismiss)
        }
    }

    private suspend fun resolveTracks(musicSet: MusicSet): List<Music> =
        observeTracksUseCase(musicSet).first()
}
