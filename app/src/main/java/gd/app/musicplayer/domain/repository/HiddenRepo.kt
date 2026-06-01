package gd.app.musicplayer.domain.repository

import gd.app.musicplayer.core.database.dao.MusicDao
import gd.app.musicplayer.core.database.entity.HiddenFolderEntity
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HiddenRepo @Inject constructor(
    private val musicDao: MusicDao
) {
    fun observeHiddenFolders(): Flow<List<MusicSet.Folder>> = musicDao.observeHiddenFolders()

    fun observeHiddenSongs(): Flow<List<Music>> = musicDao.observeHiddenSongs()

    fun observeVisibleFolders(): Flow<List<MusicSet.Folder>> = musicDao.observeFolders()


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
