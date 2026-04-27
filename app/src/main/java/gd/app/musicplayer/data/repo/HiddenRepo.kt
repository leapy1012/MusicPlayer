package gd.app.musicplayer.data.repositorysitory

import gd.app.musicplayer.data.db.dao.MusicDao
import gd.app.musicplayer.data.db.entity.HiddenFolderEntity
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import kotlinx.coroutines.flow.Flow

class HiddenRepo(
    private val musicDao: MusicDao
) {
    fun observeHiddenFolders(): Flow<List<MusicSet.Folder>> = musicDao.observeHiddenFolders()

    fun observeHiddenSongs(): Flow<List<Music>> = musicDao.observeHiddenSongs()

    fun observeVisibleFolders(): Flow<List<MusicSet.Folder>> = musicDao.observeFolders()

    fun observeVisibleSongs(): Flow<List<Music>> = musicDao.observeTracks(
        sortStyle = "title",
        sortDescending = false
    )

    suspend fun removeHiddenFolder(folderPath: String) {
        musicDao.removeHiddenFolder(folderPath)
    }

    suspend fun unhideSongs(songIds: Collection<Long>) {
        val ids = songIds.distinct()
        if (ids.isEmpty()) return
        musicDao.updateMusicHideTime(0L, ids)
    }

    suspend fun hideSelection(folderPaths: Collection<String>, songIds: Collection<Long>) {
        val normalizedFolderPaths = folderPaths.distinct().filter { it.isNotBlank() }
        val normalizedSongIds = songIds.distinct()

        if (normalizedFolderPaths.isNotEmpty()) {
            musicDao.upsertHiddenFolders(
                normalizedFolderPaths.map { HiddenFolderEntity(folderPath = it) }
            )
        }
        if (normalizedSongIds.isNotEmpty()) {
            musicDao.updateMusicHideTime(System.currentTimeMillis(), normalizedSongIds)
        }
    }
}
