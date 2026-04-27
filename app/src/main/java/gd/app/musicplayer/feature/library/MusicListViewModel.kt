package gd.app.musicplayer.feature.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.repo.LibraryRepo
import gd.app.musicplayer.domain.usecase.playback.PlayTracksUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MusicListUiState(
    val tracks: List<Music> = emptyList(),
    val artistAlbums: List<MusicSet.Album> = emptyList(),
    val isEmpty: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MusicListViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val libraryRepo: LibraryRepo,
    private val playTracksUseCase: PlayTracksUseCase
) : ViewModel() {
    private val currentMusicSet = MutableStateFlow<MusicSet?>(null)

    val uiState: StateFlow<MusicListUiState> =
        currentMusicSet
            .filterNotNull()
            .flatMapLatest { musicSet ->
                combine(
                    libraryRepo.observeTracks(musicSet),
                    observeArtistAlbums(musicSet),
                    libraryRepo.observePreferenceChanges().onStart { emit(Unit) }
                ) { tracks, albums, _ ->
                    MusicListUiState(
                        tracks = tracks,
                        artistAlbums = albums,
                        isEmpty = tracks.isEmpty()
                    )
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = MusicListUiState()
            )

    fun bind(musicSet: MusicSet) {
        if (currentMusicSet.value == musicSet) return
        currentMusicSet.value = musicSet
    }

    fun onTrackClicked(track: Music) {
        val tracks = uiState.value.tracks
        if (tracks.isEmpty()) return
        val startIndex = tracks.indexOfFirst { it._id == track._id }.takeIf { it >= 0 } ?: 0
        playTracksUseCase(appContext, tracks, startIndex)
    }

    private fun observeArtistAlbums(musicSet: MusicSet) =
        (musicSet as? MusicSet.Artist)?.let { artist ->
            libraryRepo.observeAlbumsByArtist(artist.name)
        } ?: flowOf(emptyList())
}
