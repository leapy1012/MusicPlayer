package gd.app.musicplayer.ui.feature.selection

import gd.app.musicplayer.domain.usecase.selection.MusicSelectConfirmRequest
import gd.app.musicplayer.domain.usecase.selection.MusicSelectConfirmResult
import gd.app.musicplayer.domain.usecase.selection.MusicSelectLoadRequest
import gd.app.musicplayer.domain.usecase.selection.MusicSelectLoadResult

sealed interface MusicSelectAsyncEvent {
    data class LoadCompleted(
        val request: MusicSelectLoadRequest,
        val result: MusicSelectLoadResult
    ) : MusicSelectAsyncEvent

    data class ConfirmCompleted(
        val request: MusicSelectConfirmRequest,
        val result: MusicSelectConfirmResult
    ) : MusicSelectAsyncEvent
}
