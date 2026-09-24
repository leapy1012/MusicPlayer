package gd.app.musicplayer.data.repository

import gd.app.musicplayer.core.database.dao.MusicDao
import gd.app.musicplayer.core.database.entity.HiddenFolderEntity
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.repository.HiddenRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HiddenRepoImpl @Inject constructor(
    private val musicDao: MusicDao
) : HiddenRepo {
    override fun observeHiddenFolders(): Flow<List<MusicSet.Folder>> = musicDao.observeHiddenFolders()

    override fun observeHiddenSongs(): Flow<List<Music>> = musicDao.observeHiddenSongs()

    override fun observeVisibleFolders(): Flow<List<MusicSet.Folder>> = musicDao.observeFolders()

    override fun observeVisibleSongs(): Flow<List<Music>> = musicDao.observeTracks(
        sortStyle = "title",
        sortDescending = false
    )

    override suspend fun removeHiddenFolder(folderPath: String) {
        musicDao.removeHiddenFolder(folderPath)
    }

    override suspend fun unhideSongs(songIds: Collection<Long>) {
        val ids = songIds.distinct()
        if (ids.isEmpty()) return
        musicDao.updateMusicHideTime(0L, ids)
    }

    override suspend fun hideSelection(folderPaths: Collection<String>, songIds: Collection<Long>) {
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
