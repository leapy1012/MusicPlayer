package gd.app.musicplayer.data.repo

import gd.app.musicplayer.data.db.dao.MusicDao
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.util.PreferenceUtil
import kotlinx.coroutines.flow.Flow

class MainRepo(
    private val musicDao: MusicDao,
    preferenceUtil: PreferenceUtil,
    artworkRepo: ArtworkRepo
) {
    private val libraryRepo = LibraryRepo(musicDao, preferenceUtil)
    private val playlistRepo = PlaylistRepo(musicDao, artworkRepo, preferenceUtil)
    private val searchRepo = SearchRepo(musicDao)
    private val statsRepo = StatsRepo(musicDao)

    fun observePlaylists(): Flow<List<MusicSet.Playlist>> =
        playlistRepo.observePlaylists()

    suspend fun updatePlaylistOrder(playlistIdsInDisplayOrder: List<Long>) {
        playlistRepo.updatePlaylistOrder(playlistIdsInDisplayOrder)
    }

    fun observeSearchPlaylists(query: String): Flow<List<MusicSet.Playlist>> =
        searchRepo.observeSearchPlaylists(query)

    fun observeTracksCount(): Flow<Int> = statsRepo.observeTracksCount()

    fun observeFolderCount(): Flow<Int> = statsRepo.observeFolderCount()

    fun observeFavoriteCount(): Flow<Int> = statsRepo.observeFavoriteCount()

    fun observeRecentPlayCount(
        playlistWindowMs: Long,
        windowStartMs: Long,
        playlistLimit: Int
    ): Flow<Int> = statsRepo.observeRecentPlayCount(playlistWindowMs, windowStartMs, playlistLimit)

    fun observeRecentAddCount(
        playlistWindowMs: Long,
        windowStartMs: Long,
        playlistLimit: Int
    ): Flow<Int> = statsRepo.observeRecentAddCount(playlistWindowMs, windowStartMs, playlistLimit)

    fun observeMostPlayCount(
        playlistWindowMs: Long,
        windowStartMs: Long,
        playlistLimit: Int
    ): Flow<Int> = statsRepo.observeMostPlayCount(playlistWindowMs, windowStartMs, playlistLimit)

    fun observeTracks(
        musicSet: MusicSet,
        sortStyle: String,
        sortDescending: Boolean
    ): Flow<List<Music>> = libraryRepo.observeTracks(musicSet, sortStyle, sortDescending)

    fun observeSearchTracks(query: String): Flow<List<Music>> =
        searchRepo.observeSearchTracks(query)

    fun observeAlbumsByArtist(artist: String): Flow<List<MusicSet.Album>> =
        libraryRepo.observeAlbumsByArtist(artist)

    fun observeMusicSets(type: MusicSet): Flow<List<MusicSet>> =
        libraryRepo.observeMusicSets(type)

    fun observeSearchMusicSets(type: MusicSet, query: String): Flow<List<MusicSet>> =
        searchRepo.observeSearchMusicSets(type, query)
}
