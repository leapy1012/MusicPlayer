package gd.app.musicplayer.domain.repository

import gd.app.musicplayer.core.database.dao.LibraryDao
import gd.app.musicplayer.core.datastore.PlaylistPreferenceDataStore
import gd.app.musicplayer.core.datastore.SortPreferencesDataStore
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.model.SmartPlaylistConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach

@Singleton
class LibraryRepo @Inject constructor(
    private val libraryDao: LibraryDao,
    private val sortPreference: SortPreferencesDataStore,
    private val playlistPreference: PlaylistPreferenceDataStore,
    private val snapshotCache: LibraryListSnapshotCache
) {

    /**
     * Original-style fast path: emit memory snapshot (if any) → one-shot SQLite →
     * then preference-driven Room Flow. Never block first paint on DataStore.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeTracks(musicSet: MusicSet, isSelectionMode: Boolean = false): Flow<List<Music>> {
        val cacheKey = snapshotCache.tracksKey(musicSet, isSelectionMode)

        val live = combine(
            sortPreference.observeSortStyle(musicSet, isSelectionMode),
            sortPreference.observeSortDescending(musicSet, isSelectionMode),
            playlistPreference.observeSmartPlaylistConfig()
        ) { sortStyle, sortDescending, smartConfig ->
            snapshotCache.rememberSort(cacheKey, sortStyle, sortDescending)
            snapshotCache.smartPlaylistConfig = smartConfig
            LibraryQueryBuilder.buildTrackQuery(
                musicSet = musicSet,
                sortStyle = sortStyle,
                sortDescending = sortDescending,
                smartConfig = smartConfig
            )
        }.flatMapLatest { query ->
            libraryDao.observeTracksRaw(query)
        }.onEach { tracks ->
            snapshotCache.putTracks(cacheKey, tracks)
        }

        return flow {
            snapshotCache.getTracks(cacheKey)?.let { emit(it) }

            val fastQuery = LibraryQueryBuilder.buildTrackQuery(
                musicSet = musicSet,
                sortStyle = snapshotCache.sortStyleByKey[cacheKey] ?: DEFAULT_SORT_STYLE,
                sortDescending = snapshotCache.sortDescendingByKey[cacheKey] ?: false,
                smartConfig = snapshotCache.smartPlaylistConfig ?: defaultSmartPlaylistConfig()
            )
            val fastTracks = libraryDao.getTracksRaw(fastQuery)
            snapshotCache.putTracks(cacheKey, fastTracks)
            emit(fastTracks)

            emitAll(live)
        }.distinctUntilChanged()
    }

    suspend fun getTracks(musicSet: MusicSet): List<Music> {
        val cacheKey = snapshotCache.tracksKey(musicSet, selectionMode = false)
        snapshotCache.getTracks(cacheKey)?.let { return it }

        val query = LibraryQueryBuilder.buildTrackQuery(
            musicSet = musicSet,
            sortStyle = snapshotCache.sortStyleByKey[cacheKey] ?: DEFAULT_SORT_STYLE,
            sortDescending = snapshotCache.sortDescendingByKey[cacheKey] ?: false,
            smartConfig = snapshotCache.smartPlaylistConfig ?: defaultSmartPlaylistConfig()
        )
        val tracks = libraryDao.getTracksRaw(query)
        snapshotCache.putTracks(cacheKey, tracks)
        return tracks
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAlbumsByArtist(artist: String): Flow<List<MusicSet.Album>> {
        val live = combine(
            sortPreference.observeAlbumsSortStyle(),
            sortPreference.observeAlbumsSortReversed(),
        ) { sortStyle, sortDescending ->
            LibraryQueryBuilder.buildAlbumsByArtistQuery(
                artist = artist,
                sortStyle,
                sortDescending
            )
        }.flatMapLatest { query ->
            libraryDao.observeAlbumsByArtistRaw(query)
        }

        return flow {
            val fastQuery = LibraryQueryBuilder.buildAlbumsByArtistQuery(
                artist = artist,
                sortStyle = DEFAULT_SORT_STYLE,
                sortDescending = false
            )
            emit(libraryDao.getAlbumsByArtistRaw(fastQuery))
            emitAll(live)
        }.distinctUntilChanged()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeMusicSets(type: MusicSet): Flow<List<MusicSet>> {
        val cacheKey = snapshotCache.setsKey(type)

        val live: Flow<List<MusicSet>> = when (type) {
            is MusicSet.Artists -> {
                combine(
                    sortPreference.observeArtistsSortStyle(),
                    sortPreference.observeArtistsSortReversed(),
                ) { sortStyle, sortDescending ->
                    snapshotCache.rememberSort(cacheKey, sortStyle, sortDescending)
                    LibraryQueryBuilder.buildArtistsQuery(sortStyle, sortDescending)
                }.flatMapLatest { query ->
                    libraryDao.observeArtistsRaw(query)
                }
            }

            is MusicSet.Albums -> {
                combine(
                    sortPreference.observeAlbumsSortStyle(),
                    sortPreference.observeAlbumsSortReversed(),
                ) { sortStyle, sortDescending ->
                    snapshotCache.rememberSort(cacheKey, sortStyle, sortDescending)
                    LibraryQueryBuilder.buildAlbumsQuery(sortStyle, sortDescending)
                }.flatMapLatest { query ->
                    libraryDao.observeAlbumsRaw(query)
                }
            }

            is MusicSet.Genres -> {
                combine(
                    sortPreference.observeGenresSortStyle(),
                    sortPreference.observeGenresSortReversed(),
                ) { sortStyle, sortDescending ->
                    snapshotCache.rememberSort(cacheKey, sortStyle, sortDescending)
                    LibraryQueryBuilder.buildGenresQuery(sortStyle, sortDescending)
                }.flatMapLatest { query ->
                    libraryDao.observeGenresRaw(query)
                }
            }

            is MusicSet.Folders -> {
                combine(
                    sortPreference.observeFoldersSortStyle(),
                    sortPreference.observeFoldersSortReversed(),
                ) { sortStyle, sortDescending ->
                    snapshotCache.rememberSort(cacheKey, sortStyle, sortDescending)
                    LibraryQueryBuilder.buildFoldersQuery(sortStyle, sortDescending)
                }.flatMapLatest { query ->
                    libraryDao.observeFoldersRaw(query)
                }
            }

            else -> flowOf(emptyList())
        }.onEach { sets ->
            snapshotCache.putSets(cacheKey, sets)
        }

        return flow {
            snapshotCache.getSets(cacheKey)?.let { emit(it) }

            val style = snapshotCache.sortStyleByKey[cacheKey] ?: DEFAULT_SORT_STYLE
            val descending = snapshotCache.sortDescendingByKey[cacheKey] ?: false
            val fastSets = when (type) {
                is MusicSet.Artists -> {
                    libraryDao.getArtistsRaw(
                        LibraryQueryBuilder.buildArtistsQuery(style, descending)
                    )
                }

                is MusicSet.Albums -> {
                    libraryDao.getAlbumsRaw(
                        LibraryQueryBuilder.buildAlbumsQuery(style, descending)
                    )
                }

                is MusicSet.Genres -> {
                    libraryDao.getGenresRaw(
                        LibraryQueryBuilder.buildGenresQuery(style, descending)
                    )
                }

                is MusicSet.Folders -> {
                    libraryDao.getFoldersRaw(
                        LibraryQueryBuilder.buildFoldersQuery(style, descending)
                    )
                }

                else -> emptyList()
            }
            snapshotCache.putSets(cacheKey, fastSets)
            emit(fastSets)

            emitAll(live)
        }.distinctUntilChanged()
    }

    suspend fun clearFavorites() {
        libraryDao.clearPlaylistEntries(MusicSet.FAVORITES)
    }

    suspend fun clearRecentlyPlayed() {
        libraryDao.clearRecentlyPlayedStats()
    }

    suspend fun removeFromRecentlyPlayed(trackId: Long) {
        libraryDao.clearTrackRecentlyPlayedStats(trackId)
    }

    suspend fun clearMostPlayed() {
        libraryDao.clearMostPlayedStats()
    }

    suspend fun removeFromMostPlayed(trackId: Long) {
        libraryDao.clearTrackMostPlayedStats(trackId)
    }

    suspend fun clearRecentlyAdded() {
        libraryDao.clearRecentlyAddedStats()
    }

    suspend fun hideTracks(trackIds: Collection<Long>, hideTime: Long) {
        val ids = trackIds.distinct()
        if (ids.isEmpty()) return
        libraryDao.updateMusicHideTime(hideTime, ids)
    }

    suspend fun deleteTracksFromLibrary(trackIds: Collection<Long>, stateTime: Long) {
        val ids = trackIds.distinct()
        if (ids.isEmpty()) return
        libraryDao.updateMusicVisibleState(
            visibleState = 0,
            stateTime = stateTime,
            musicIds = ids
        )
        libraryDao.deleteMusicPlaylistRefsByTrackIds(ids)
    }

    fun observeDeletedSongs(): Flow<List<Music>> = libraryDao.observeDeletedSongs()

    suspend fun restoreDeletedSongs(trackIds: Collection<Long>) {
        val ids = trackIds.distinct()
        if (ids.isEmpty()) return
        libraryDao.restoreDeletedSongs(ids)
    }

    suspend fun markSourceDeletedSongs(trackIds: Collection<Long>) {
        val ids = trackIds.distinct()
        if (ids.isEmpty()) return
        libraryDao.markSourceDeletedSongs(ids, System.currentTimeMillis())
    }

    suspend fun markDeletedSourceFilesRemoved(trackIds: Collection<Long>) {
        val ids = trackIds.distinct()
        if (ids.isEmpty()) return
        libraryDao.markDeletedSourceFilesRemoved(ids, System.currentTimeMillis())
    }

    private companion object {
        const val DEFAULT_SORT_STYLE = "name"
        const val DEFAULT_SMART_WINDOW_MS = 15552000000L // 6 months — matches PlaylistPreferenceDataStore

        fun defaultSmartPlaylistConfig(): SmartPlaylistConfig {
            val now = System.currentTimeMillis()
            return SmartPlaylistConfig(
                windowStartMs = now - DEFAULT_SMART_WINDOW_MS,
                windowDurationMs = DEFAULT_SMART_WINDOW_MS,
                trackLimit = -1
            )
        }
    }
}
