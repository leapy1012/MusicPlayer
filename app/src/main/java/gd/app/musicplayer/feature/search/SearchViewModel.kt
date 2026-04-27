package gd.app.musicplayer.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.data.model.ListItem
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.repository.LibraryRepo
import gd.app.musicplayer.data.repository.PlaylistRepo
import gd.app.musicplayer.data.repository.SearchRepo
import gd.app.musicplayer.util.PreferenceUtil
import kotlin.random.Random
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val libraryRepo: LibraryRepo,
    private val playlistRepo: PlaylistRepo,
    private val searchRepo: SearchRepo,
    private val preferenceUtil: PreferenceUtil
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val sortVersion = MutableStateFlow(0)

    private data class SearchSource(
        val tracks: List<Music>,
        val albums: List<MusicSet>,
        val artists: List<MusicSet>,
        val folders: List<MusicSet>,
        val playlists: List<MusicSet.Playlist>
    )

    val sections: StateFlow<List<SearchResultAdapter.SearchSection>> =
        combine(
            query.debounce(200).distinctUntilChanged(),
            sortVersion
        ) { rawQuery, _ -> rawQuery.trim() }
            .flatMapLatest(::observeSearchSource)
            .map { source ->
                buildSections(
                    tracks = source.tracks,
                    albums = source.albums,
                    artists = source.artists,
                    folders = source.folders,
                    playlists = source.playlists
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    fun setQuery(query: String) {
        this.query.value = query
    }

    fun refreshSortOrder() {
        sortVersion.value += 1
    }

    suspend fun resolvePlaybackQueue(clickedTrack: Music): Pair<List<Music>, Int> {
        val queue = if (preferenceUtil.getQueueForSearchingMode() == 0) {
            libraryRepo.observeTracks(MusicSet.Tracks).first()
        } else {
            sections.value.firstOrNull { section ->
                section.items.firstOrNull() is ListItem.MusicItem
            }?.items?.mapNotNull { (it as? ListItem.MusicItem)?.music }.orEmpty()
        }.ifEmpty { listOf(clickedTrack) }

        val startIndex = queue.indexOfFirst { it._id == clickedTrack._id }.coerceAtLeast(0)
        return queue to startIndex
    }

    private fun observeSearchSource(query: String) = combine(
        observeTracksSource(query),
        observeBrowseSource(query, MusicSet.Albums),
        observeBrowseSource(query, MusicSet.Artists),
        observeBrowseSource(query, MusicSet.Folders),
        observePlaylistsSource(query)
    ) { tracks, albums, artists, folders, playlists ->
        SearchSource(
            tracks = tracks,
            albums = albums,
            artists = artists,
            folders = folders,
            playlists = playlists
        )
    }

    private fun observeTracksSource(query: String) =
        if (query.isBlank()) {
            libraryRepo.observeTracks(
                musicSet = MusicSet.Tracks,
                sortStyle = "title",
                sortDescending = false
            )
        } else {
            searchRepo.observeSearchTracks(query)
        }

    private fun observeBrowseSource(query: String, musicSet: MusicSet) =
        if (query.isBlank()) {
            libraryRepo.observeMusicSets(musicSet)
        } else {
            searchRepo.observeSearchMusicSets(musicSet, query)
        }

    private fun observePlaylistsSource(query: String) =
        if (query.isBlank()) {
            playlistRepo.observePlaylists()
        } else {
            searchRepo.observeSearchPlaylists(query)
        }

    private fun buildSections(
        tracks: List<Music>,
        albums: List<MusicSet>,
        artists: List<MusicSet>,
        folders: List<MusicSet>,
        playlists: List<MusicSet.Playlist>
    ): List<SearchResultAdapter.SearchSection> {
        val sortedTracks = sortTracks(tracks)
        val sortedAlbums = sortAlbums(albums)
        val sortedArtists = sortArtists(artists)
        val sortedFolders = sortFolders(folders)
        val sortedPlaylists = sortPlaylists(playlists)

        return buildList {
            if (sortedTracks.isNotEmpty()) {
                add(
                    SearchResultAdapter.SearchSection(
                        titleRes = R.string.songs,
                        items = sortedTracks.map { ListItem.MusicItem(it) }
                    )
                )
            }
            if (sortedAlbums.isNotEmpty()) {
                add(
                    SearchResultAdapter.SearchSection(
                        titleRes = R.string.albums,
                        items = sortedAlbums.map { ListItem.MusicSetItem(it) }
                    )
                )
            }
            if (sortedArtists.isNotEmpty()) {
                add(
                    SearchResultAdapter.SearchSection(
                        titleRes = R.string.artists,
                        items = sortedArtists.map { ListItem.MusicSetItem(it) }
                    )
                )
            }
            if (sortedFolders.isNotEmpty()) {
                add(
                    SearchResultAdapter.SearchSection(
                        titleRes = R.string.folders,
                        items = sortedFolders.map { ListItem.MusicSetItem(it) }
                    )
                )
            }
            if (sortedPlaylists.isNotEmpty()) {
                add(
                    SearchResultAdapter.SearchSection(
                        titleRes = R.string.playlists,
                        items = sortedPlaylists.map { ListItem.MusicSetItem(it) }
                    )
                )
            }
        }
    }

    private fun sortTracks(items: List<Music>): List<Music> {
        val style = preferenceUtil.getSortStyle(MusicSet.Tracks)
        val reversed = preferenceUtil.isSortReversed(MusicSet.Tracks, false)
        val comparator = when (style) {
            "title", "title_desc" -> compareBy<Music>({ it.title.lowercase() }, { it._id })
            "track" -> compareBy<Music>({ it.p_id }, { it.title.lowercase() }, { it._id })
            "year" -> compareBy<Music>({ it.year ?: 0 }, { it.title.lowercase() }, { it._id })
            "artist" -> compareBy<Music>({ it.artist.lowercase() }, { it.title.lowercase() }, { it._id })
            "album" -> compareBy<Music>({ it.album.lowercase() }, { it.title.lowercase() }, { it._id })
            "folder" -> compareBy<Music>({ it.folder_path.orEmpty().lowercase() }, { it.title.lowercase() }, { it._id })
            "date" -> compareBy<Music>({ it.date ?: 0L }, { it.title.lowercase() }, { it._id })
            "size" -> compareBy<Music>({ it.size ?: 0L }, { it.title.lowercase() }, { it._id })
            "duration" -> compareBy<Music>({ it.duration }, { it.title.lowercase() }, { it._id })
            else -> compareBy<Music>({ it.title.lowercase() }, { it._id })
        }
        val sorted = when (style) {
            "random" -> items.shuffled(Random(sortVersion.value))
            else -> items.sortedWith(comparator)
        }
        return when {
            style == "title_desc" -> sorted.asReversed()
            reversed -> sorted.asReversed()
            else -> sorted
        }
    }

    private fun sortAlbums(items: List<MusicSet>): List<MusicSet> {
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

    private fun sortArtists(items: List<MusicSet>): List<MusicSet> {
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

    private fun sortFolders(items: List<MusicSet>): List<MusicSet> {
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

    private fun sortPlaylists(items: List<MusicSet.Playlist>): List<MusicSet.Playlist> =
        items.sortedWith(compareBy({ it.sort }, { it.setup_time }, { it.name.lowercase() }, { it.id }))
}
