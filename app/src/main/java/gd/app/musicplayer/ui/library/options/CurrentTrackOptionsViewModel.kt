package gd.app.musicplayer.ui.library.options

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.R
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.usecase.track.DeleteTracksUseCase
import gd.app.musicplayer.domain.usecase.track.HideTracksUseCase
import gd.app.musicplayer.playback.SleepTimerManager
import gd.app.musicplayer.playback.SleepTimerState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import javax.inject.Inject

data class CurrentTrackOptionsUiState(
    val music: Music? = null,
    val sleepMenuLabel: String = ""
)

sealed interface CurrentTrackOptionsEvent {
    data class ShowToast(@StringRes val messageRes: Int) : CurrentTrackOptionsEvent
    data class OpenAddTo(val tracks: List<Music>) : CurrentTrackOptionsEvent
    data class OpenArtist(val artist: MusicSet.Artist) : CurrentTrackOptionsEvent
    data class OpenAlbum(val album: MusicSet.Album) : CurrentTrackOptionsEvent
}

@HiltViewModel
class CurrentTrackOptionsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val hideTracksUseCase: HideTracksUseCase,
    private val deleteTracksUseCase: DeleteTracksUseCase
) : ViewModel() {

    private val musicState = MutableStateFlow<Music?>(null)
    private val eventsChannel = Channel<CurrentTrackOptionsEvent>(Channel.BUFFERED)
    val events: Flow<CurrentTrackOptionsEvent> = eventsChannel.receiveAsFlow()

    val uiState: StateFlow<CurrentTrackOptionsUiState> = combine(
        musicState,
        SleepTimerManager.state
    ) { music, sleepState ->
        CurrentTrackOptionsUiState(
            music = music,
            sleepMenuLabel = buildSleepTimerLabel(sleepState)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CurrentTrackOptionsUiState()
    )

    fun initialize(music: Music) {
        if (musicState.value != null) return
        musicState.value = music
    }

    fun onAddToClicked() {
        val music = musicState.value ?: return
        viewModelScope.launch { eventsChannel.send(CurrentTrackOptionsEvent.OpenAddTo(listOf(music))) }
    }

    fun onViewArtistClicked() {
        val music = musicState.value ?: return
        val artistName = music.artist.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            eventsChannel.send(
                CurrentTrackOptionsEvent.OpenArtist(
                    MusicSet.Artist(
                        id = MusicSet.ARTISTS,
                        name = artistName,
                        musicCount = 0,
                        albumCount = 0,
                        albumArt = music.albumPicture
                    )
                )
            )
        }
    }

    fun onViewAlbumClicked() {
        val music = musicState.value ?: return
        val albumName = music.album.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            eventsChannel.send(
                CurrentTrackOptionsEvent.OpenAlbum(
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
        }
    }

    fun hideTrack() {
        val music = musicState.value ?: return
        viewModelScope.launch {
            hideTracksUseCase(listOf(music.id))
            eventsChannel.send(CurrentTrackOptionsEvent.ShowToast(R.string.hidden_folders_tips))
        }
    }

    fun deleteTrack() {
        val music = musicState.value ?: return
        viewModelScope.launch {
            val deletedCount = deleteTracksUseCase(listOf(music))
            eventsChannel.send(
                CurrentTrackOptionsEvent.ShowToast(
                    if (deletedCount > 0) R.string.succeed else R.string.feature_not_implemented
                )
            )
        }
    }

    private fun buildSleepTimerLabel(state: SleepTimerState): String {
        if (!state.isActive) return appContext.getString(R.string.sleep_timer_2)
        val detail = when {
            state.stopAfterCurrentTrack -> appContext.getString(R.string.sleep_end_stop)
            state.action == SleepTimerState.ACTION_EXIT_PLAYER -> appContext.getString(R.string.sleep_end_exit)
            else -> {
                val minutes = (state.remainingMs / 60_000f).roundToInt().coerceAtLeast(1)
                appContext.getString(R.string.sleep_mode_tips, minutes.toString())
            }
        }
        return appContext.getString(R.string.sleep_timer_2) + "\n" + detail
    }
}
