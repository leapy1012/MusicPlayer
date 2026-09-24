package gd.app.musicplayer.domain.repository

import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import kotlinx.coroutines.flow.Flow

interface HiddenRepo {
    fun observeHiddenFolders(): Flow<List<MusicSet.Folder>>
    fun observeHiddenSongs(): Flow<List<Music>>
    fun observeVisibleFolders(): Flow<List<MusicSet.Folder>>
    fun observeVisibleSongs(): Flow<List<Music>>

    suspend fun removeHiddenFolder(folderPath: String)
    suspend fun unhideSongs(songIds: Collection<Long>)
    suspend fun hideSelection(folderPaths: Collection<String>, songIds: Collection<Long>)
}
