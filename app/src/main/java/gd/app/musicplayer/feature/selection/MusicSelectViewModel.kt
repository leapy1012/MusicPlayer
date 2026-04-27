package gd.app.musicplayer.feature.selection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.app.AppDispatchers
import gd.app.musicplayer.data.repository.LibraryRepo
import gd.app.musicplayer.data.repository.PlaylistRepo
import gd.app.musicplayer.util.PreferenceUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MusicSelectViewModel @Inject constructor(
    libraryRepo: LibraryRepo,
    playlistRepo: PlaylistRepo,
    preferenceUtil: PreferenceUtil,
    private val dispatchers: AppDispatchers
) : ViewModel() {

    private val loadUseCase = LoadMusicSelectDataUseCase(
        libraryRepo = libraryRepo,
        playlistRepo = playlistRepo,
        preferenceUtil = preferenceUtil
    )
    private val confirmUseCase = ConfirmMusicSelectUseCase(
        playlistRepo = playlistRepo
    )

    private val _events = MutableSharedFlow<MusicSelectAsyncEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<MusicSelectAsyncEvent> = _events.asSharedFlow()

    private var loadJob: Job? = null

    fun load(request: MusicSelectLoadRequest) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(dispatchers.io) {
            val result = loadUseCase(request)
            _events.emit(MusicSelectAsyncEvent.LoadCompleted(request, result))
        }
    }

    fun confirm(request: MusicSelectConfirmRequest) {
        viewModelScope.launch(dispatchers.io) {
            val result = confirmUseCase(request)
            _events.emit(MusicSelectAsyncEvent.ConfirmCompleted(request, result))
        }
    }
}
