package gd.app.musicplayer.feature.player

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.domain.usecase.playback.PlayNextTrackUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayPreviousTrackUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.SeekToPositionUseCase
import gd.app.musicplayer.domain.usecase.playback.TogglePlayPauseUseCase
import gd.app.musicplayer.domain.usecase.playlist.ToggleFavoriteTrackUseCase
import gd.app.musicplayer.playback.MusicPlaybackController
import javax.inject.Inject

@HiltViewModel
class MusicPlayViewModel @Inject constructor(
    private val togglePlayPauseUseCase: TogglePlayPauseUseCase,
    private val playPreviousTrackUseCase: PlayPreviousTrackUseCase,
    private val playNextTrackUseCase: PlayNextTrackUseCase,
    private val seekToPositionUseCase: SeekToPositionUseCase,
    private val playTracksUseCase: PlayTracksUseCase,
    private val toggleFavoriteTrackUseCase: ToggleFavoriteTrackUseCase
) : ViewModel() {

    val playbackState = MusicPlaybackController.state

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
        return MusicPlaybackController.formatTime(positionMs)
    }

    suspend fun toggleFavorite(trackId: Long): Boolean {
        return toggleFavoriteTrackUseCase(trackId)
    }
}
