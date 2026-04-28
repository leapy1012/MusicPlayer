package gd.app.musicplayer.ui.feature.selection

import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet

data class MusicSelectLoadRequest(
    val sourceMode: Boolean,
    val selectedSet: MusicSet,
    val sourceCategory: MusicSet,
    val targetSet: MusicSet?
)

data class MusicSelectLoadResult(
    val selectedSetSongs: List<Music> = emptyList(),
    val targetSetSongs: List<Music> = emptyList(),
    val sourceSets: List<MusicSet> = emptyList(),
    val spinnerCandidates: List<MusicSet> = emptyList()
)

data class MusicSelectConfirmRequest(
    val selectedSongs: List<Music>,
    val targetSet: MusicSet
)

data class MusicSelectConfirmResult(
    val insertedCount: Int,
    val skippedCount: Int
)

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
