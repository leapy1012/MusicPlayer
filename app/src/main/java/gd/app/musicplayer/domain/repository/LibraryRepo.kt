package gd.app.musicplayer.domain.repository

import gd.app.musicplayer.data.local.preference.SortPreferencesDataStore
import gd.app.musicplayer.data.local.preference.PlaylistPreferenceDataStore
import gd.app.musicplayer.data.local.db.dao.LibraryDao
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepo @Inject constructor(
    private val libraryDao: LibraryDao,
    private val sortPreference: SortPreferencesDataStore,
    private val playlistPreference: PlaylistPreferenceDataStore,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeTracks(musicSet: MusicSet, isSelectionMode: Boolean = false): Flow<List<Music>> {
        return combine(
            sortPreference.observeSortStyle(musicSet, isSelectionMode),
            sortPreference.observeSortDescending(musicSet, isSelectionMode),
            playlistPreference.observeSmartPlaylistConfig()
        ) { sortStyle, sortDescending, smartConfig ->
            LibraryQueryBuilder.buildTrackQuery(
                musicSet = musicSet,
                sortStyle = sortStyle,
                sortDescending = sortDescending,
                smartConfig = smartConfig
            )
        }.flatMapLatest { query ->
            libraryDao.observeTracksRaw(query)
        }
    }

    suspend fun getTracks(musicSet: MusicSet) : List<Music> {
        return observeTracks(musicSet).first()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAlbumsByArtist(artist: String): Flow<List<MusicSet.Album>> {
        return combine(
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
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeMusicSets(type: MusicSet): Flow<List<MusicSet>> {
        return when (type) {
            is MusicSet.Artists -> {
                combine(
                    sortPreference.observeArtistsSortStyle(),
                    sortPreference.observeArtistsSortReversed(),
                ) { sortStyle, sortDescending ->
                    LibraryQueryBuilder.buildArtistsQuery(
                        sortStyle,
                        sortDescending
                    )
                }.flatMapLatest { query ->
                    libraryDao.observeArtistsRaw(query)
                }
            }

            is MusicSet.Albums -> {
                combine(
                    sortPreference.observeAlbumsSortStyle(),
                    sortPreference.observeAlbumsSortReversed(),
                ) { sortStyle, sortDescending ->
                    LibraryQueryBuilder.buildAlbumsQuery(
                        sortStyle,
                        sortDescending
                    )
                }.flatMapLatest { query ->
                    libraryDao.observeAlbumsRaw(query)
                }
            }

            is MusicSet.Genres -> {
                combine(
                    sortPreference.observeGenresSortStyle(),
                    sortPreference.observeGenresSortReversed(),
                ) { sortStyle, sortDescending ->
                    LibraryQueryBuilder.buildGenresQuery(
                        sortStyle,
                        sortDescending
                    )
                }.flatMapLatest { query ->
                    libraryDao.observeGenresRaw(query)
                }
            }

            is MusicSet.Folders -> {
                combine(
                    sortPreference.observeFoldersSortStyle(),
                    sortPreference.observeFoldersSortReversed(),
                ) { sortStyle, sortDescending ->
                    LibraryQueryBuilder.buildFoldersQuery(
                        sortStyle,
                        sortDescending
                    )
                }.flatMapLatest { query ->
                    libraryDao.observeFoldersRaw(query)
                }
            }

            else -> flowOf(emptyList())
        }
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
}
