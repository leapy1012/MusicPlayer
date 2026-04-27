package gd.app.musicplayer.data.db.dao

import androidx.room.Dao

@Dao
interface MusicDao : LibraryDao, PlaylistDao, SearchDao, StatsDao
