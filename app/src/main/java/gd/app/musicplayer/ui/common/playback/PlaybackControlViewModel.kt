package gd.app.musicplayer.ui.common.playback

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.repo.MainRepo
import gd.app.musicplayer.domain.usecase.playback.PlayNextTrackUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayPreviousTrackUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.SeekToPositionUseCase
import gd.app.musicplayer.domain.usecase.playback.TogglePlayPauseUseCase
import gd.app.musicplayer.playback.MusicPlaybackController
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class PlaybackControlViewModel @Inject constructor(
    private val mainRepo: MainRepo,
    private val playTracksUseCase: PlayTracksUseCase,
    private val togglePlayPauseUseCase: TogglePlayPauseUseCase,
    private val playPreviousTrackUseCase: PlayPreviousTrackUseCase,
    private val playNextTrackUseCase: PlayNextTrackUseCase,
    private val seekToPositionUseCase: SeekToPositionUseCase
) : ViewModel() {

    val playbackState = MusicPlaybackController.state

    suspend fun playAllTracks(context: Context) {
        val tracks = mainRepo.observeTracks(
            musicSet = MusicSet.Tracks,
            sortStyle = "title",
            sortDescending = false
        ).first()
        if (tracks.isNotEmpty()) {
            playTracksUseCase(context, tracks, 0)
        }
    }

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

    fun replaceQueue(context: Context, tracks: List<Music>, currentIndex: Int) {
        MusicPlaybackController.replaceQueue(context, tracks, currentIndex)
    }

    fun clearQueue(context: Context) {
        MusicPlaybackController.clearQueue(context)
    }

    fun formatTime(positionMs: Int): String {
        return MusicPlaybackController.formatTime(positionMs)
    }
}
