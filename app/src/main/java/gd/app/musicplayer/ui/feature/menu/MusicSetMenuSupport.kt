package gd.app.musicplayer.ui.feature.menu

import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.model.isBrowseCategory
import gd.app.musicplayer.data.model.isConcreteCollection
import gd.app.musicplayer.data.model.isTrackCollection
internal val MusicSet.supportsViewModeMenu: Boolean
    get() = this is MusicSet.Artists || this is MusicSet.Albums || this is MusicSet.Genres

internal val MusicSet.supportsSortMenu: Boolean
    get() = isTrackCollection

internal val MusicSet.supportsShuffleAllMenu: Boolean
    get() = isTrackCollection

internal val MusicSet.supportsPlayNextMenu: Boolean
    get() = isTrackCollection && this !is MusicSet.Tracks
internal val MusicSet.supportsPlaylistManagementMenu: Boolean
    get() = isTrackCollection

internal val MusicSet.opensTrackCollectionScreen: Boolean
    get() = isConcreteCollection

internal val MusicSet.supportsCompactAlbumHeader: Boolean
    get() = this is MusicSet.Folder || this is MusicSet.Playlist || this is MusicSet.Favorites || this is MusicSet.RecentlyAdded || this is MusicSet.RecentlyPlayed || this is MusicSet.MostPlayed
