package gd.app.musicplayer.ui.feature.player

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.domain.usecase.library.GetAllTracksByCurrentSortUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayNextTrackUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayPreviousTrackUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.FormatPlaybackTimeUseCase
import gd.app.musicplayer.domain.usecase.playback.ObservePlaybackStateUseCase
import gd.app.musicplayer.domain.usecase.playback.SeekToPositionUseCase
import gd.app.musicplayer.domain.usecase.playback.TogglePlayPauseUseCase
import gd.app.musicplayer.domain.usecase.playlist.ToggleFavoriteTrackUseCase
import gd.app.musicplayer.playback.MusicPlaybackState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MusicPlayViewModel @Inject constructor(
    private val getAllTracksByCurrentSortUseCase: GetAllTracksByCurrentSortUseCase,
    private val togglePlayPauseUseCase: TogglePlayPauseUseCase,
    private val playPreviousTrackUseCase: PlayPreviousTrackUseCase,
    private val playNextTrackUseCase: PlayNextTrackUseCase,
    private val seekToPositionUseCase: SeekToPositionUseCase,
    private val playTracksUseCase: PlayTracksUseCase,
    private val toggleFavoriteTrackUseCase: ToggleFavoriteTrackUseCase,
    private val observePlaybackStateUseCase: ObservePlaybackStateUseCase,
    private val formatPlaybackTimeUseCase: FormatPlaybackTimeUseCase
) : ViewModel() {

    val playbackState: StateFlow<MusicPlaybackState> =
        observePlaybackStateUseCase()

    fun togglePlayPause(context: Context) {
        togglePlayPauseUseCase(context)
    }

    fun playPrevious(context: Context) {
        playPreviousTrackUseCase(context)
    }

    fun playNext(context: Context) {
        playNextTrackUseCase(context)
    }

    fun seekTo(context: Context, positionMs: Int) {
        seekToPositionUseCase(context, positionMs)
    }

    fun playQueue(context: Context, tracks: List<Music>, startIndex: Int) {
        playTracksUseCase(context, tracks, startIndex)
    }

    fun formatTime(positionMs: Int): String {
        return formatPlaybackTimeUseCase(positionMs)
    }

    suspend fun playAllTracks(context: Context) {
        val tracks = getAllTracksByCurrentSortUseCase(context)
        if (tracks.isNotEmpty()) {
            playTracksUseCase(context, tracks, 0)
        }
    }

    suspend fun toggleFavorite(trackId: Long): Boolean {
        return toggleFavoriteTrackUseCase(trackId)
    }
}
