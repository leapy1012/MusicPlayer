package gd.app.musicplayer.core.database.dao

import androidx.room.Dao

@Dao
interface MusicDao : LibraryDao, PlaylistDao, SearchDao, StatsDao, EqualizerDao
