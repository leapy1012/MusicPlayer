package gd.app.musicplayer.ui.common.menu

sealed interface ContextMenuAction {
    data object Select : ContextMenuAction
    data object ShuffleAll : ContextMenuAction
    data object ViewAsList : ContextMenuAction
    data object ViewAsGrid : ContextMenuAction
    data object PlayNext : ContextMenuAction
    data object AddToQueue : ContextMenuAction
    data object AddToPlaylist : ContextMenuAction
    data object AddToHomeScreen : ContextMenuAction
    data object Rename : ContextMenuAction
    data object ManageArtwork : ContextMenuAction
    data object DeletePlaylist : ContextMenuAction
    data object ClearFavorites : ContextMenuAction
    data object ClearRecentlyAdded : ContextMenuAction
    data object ClearRecentlyPlayed : ContextMenuAction
    data object ClearMostPlayed : ContextMenuAction
    data object BackupPlaylists : ContextMenuAction
    data object RestorePlaylists : ContextMenuAction
    data object DeleteEmptyPlaylists : ContextMenuAction

    data class SortChanged(
        val sortKey: String,
        val descending: Boolean
    ) : ContextMenuAction
}
