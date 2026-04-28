package gd.app.musicplayer.ui.common.menu

sealed interface MusicSetMenuAction {
    data object Select : MusicSetMenuAction
    data object ShuffleAll : MusicSetMenuAction
    data object ViewAsList : MusicSetMenuAction
    data object ViewAsGrid : MusicSetMenuAction
    data object SortBy : MusicSetMenuAction
    data object PlayNext : MusicSetMenuAction
    data object AddToQueue : MusicSetMenuAction
    data object AddToPlaylist : MusicSetMenuAction
    data object AddToHomeScreen : MusicSetMenuAction
    data object Rename : MusicSetMenuAction
    data object ManageArtwork : MusicSetMenuAction
    data object DeletePlaylist : MusicSetMenuAction
    data object ClearFavorites : MusicSetMenuAction
    data object ClearRecentlyPlayed : MusicSetMenuAction
    data object ClearMostPlayed : MusicSetMenuAction
    data object BackupPlaylists : MusicSetMenuAction
    data object RestorePlaylists : MusicSetMenuAction
    data object DeleteEmptyPlaylists : MusicSetMenuAction

    data class SortChanged(
        val sortKey: String,
        val descending: Boolean
    ) : MusicSetMenuAction
}