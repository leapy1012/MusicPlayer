package gd.app.musicplayer.domain.usecase.search

import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.util.PreferenceUtil
import javax.inject.Inject
import kotlin.random.Random

class SortSearchResultsUseCase @Inject constructor(
    private val preferenceUtil: PreferenceUtil
) {
    fun sortTracks(items: List<Music>, sortVersion: Int): List<Music> {
        val style = preferenceUtil.getSortStyle(MusicSet.Tracks)
        val reversed = preferenceUtil.isSortReversed(MusicSet.Tracks, false)
        val comparator = when (style) {
            "title", "title_desc" -> compareBy<Music>({ it.title.lowercase() }, { it.id })
            "track" -> compareBy<Music>({ it.playlistId }, { it.title.lowercase() }, { it.id })
            "year" -> compareBy<Music>({ it.year ?: 0 }, { it.title.lowercase() }, { it.id })
            "artist" -> compareBy<Music>({ it.artist.lowercase() }, { it.title.lowercase() }, { it.id })
            "album" -> compareBy<Music>({ it.album.lowercase() }, { it.title.lowercase() }, { it.id })
            "folder" -> compareBy<Music>({ it.folderPath.orEmpty().lowercase() }, { it.title.lowercase() }, { it.id })
            "date" -> compareBy<Music>({ it.date ?: 0L }, { it.title.lowercase() }, { it.id })
            "size" -> compareBy<Music>({ it.size ?: 0L }, { it.title.lowercase() }, { it.id })
            "duration" -> compareBy<Music>({ it.duration }, { it.title.lowercase() }, { it.id })
            else -> compareBy<Music>({ it.title.lowercase() }, { it.id })
        }
        val sorted = when (style) {
            "random" -> items.shuffled(Random(sortVersion))
            else -> items.sortedWith(comparator)
        }
        return when {
            style == "title_desc" -> sorted.asReversed()
            reversed -> sorted.asReversed()
            else -> sorted
        }
    }

    fun sortAlbums(items: List<MusicSet>): List<MusicSet> {
        val albums = items.filterIsInstance<MusicSet.Album>()
        val style = preferenceUtil.getAlbumSortStyle()
        val reversed = preferenceUtil.isAlbumSortReversed()
        val comparator = when (style) {
            "title", "title_desc", "album" -> compareBy<MusicSet.Album>({ it.name.lowercase() }, { it.id })
            "year" -> compareBy<MusicSet.Album>({ it.year }, { it.name.lowercase() }, { it.id })
            "artist" -> compareBy<MusicSet.Album>({ it.artist.lowercase() }, { it.name.lowercase() }, { it.id })
            "track_count" -> compareBy<MusicSet.Album>({ it.musicCount }, { it.name.lowercase() }, { it.id })
            "date" -> compareBy<MusicSet.Album>({ it.date }, { it.name.lowercase() }, { it.id })
            else -> compareBy<MusicSet.Album>({ it.name.lowercase() }, { it.id })
        }
        val sorted = albums.sortedWith(comparator)
        return when {
            style == "title_desc" -> sorted.asReversed()
            reversed -> sorted.asReversed()
            else -> sorted
        }
    }

    fun sortArtists(items: List<MusicSet>): List<MusicSet> {
        val artists = items.filterIsInstance<MusicSet.Artist>()
        val style = preferenceUtil.getArtistSortStyle()
        val reversed = preferenceUtil.isArtistSortReversed()
        val comparator = when (style) {
            "title", "title_desc", "artist" -> compareBy<MusicSet.Artist>({ it.name.lowercase() }, { it.id })
            "track_count" -> compareBy<MusicSet.Artist>({ it.musicCount }, { it.name.lowercase() }, { it.id })
            "album_count" -> compareBy<MusicSet.Artist>({ it.albumCount }, { it.name.lowercase() }, { it.id })
            else -> compareBy<MusicSet.Artist>({ it.name.lowercase() }, { it.id })
        }
        val sorted = artists.sortedWith(comparator)
        return when {
            style == "title_desc" -> sorted.asReversed()
            reversed -> sorted.asReversed()
            else -> sorted
        }
    }

    fun sortFolders(items: List<MusicSet>): List<MusicSet> {
        val folders = items.filterIsInstance<MusicSet.Folder>()
        val style = preferenceUtil.getFolderSortStyle(false)
        val reversed = preferenceUtil.isFolderSortReversed(false)
        val comparator = when (style) {
            "name", "title", "title_desc" -> compareBy<MusicSet.Folder>({ it.name.lowercase() }, { it.id })
            "track_count" -> compareBy<MusicSet.Folder>({ it.musicCount }, { it.name.lowercase() }, { it.id })
            "date" -> compareBy<MusicSet.Folder>({ it.date }, { it.name.lowercase() }, { it.id })
            else -> compareBy<MusicSet.Folder>({ it.name.lowercase() }, { it.id })
        }
        val sorted = folders.sortedWith(comparator)
        return when {
            style == "title_desc" -> sorted.asReversed()
            reversed -> sorted.asReversed()
            else -> sorted
        }
    }

    fun sortPlaylists(items: List<MusicSet.Playlist>): List<MusicSet.Playlist> =
        items.sortedWith(compareBy({ it.sort }, { it.setup_time }, { it.name.lowercase() }, { it.id }))
}
