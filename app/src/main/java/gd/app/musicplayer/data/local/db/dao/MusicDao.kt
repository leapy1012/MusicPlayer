package gd.app.musicplayer.data.local.db.dao

import androidx.room.Dao

@Dao
interface MusicDao : LibraryDao, PlaylistDao, SearchDao, StatsDao, EqualizerDao
