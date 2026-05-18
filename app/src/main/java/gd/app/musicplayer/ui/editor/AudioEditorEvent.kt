package gd.app.musicplayer.ui.editor

sealed interface AudioEditorEvent {
    data object CloseScreen : AudioEditorEvent
    data object ClipSaved : AudioEditorEvent
    data class ShowError(val message: String) : AudioEditorEvent
}