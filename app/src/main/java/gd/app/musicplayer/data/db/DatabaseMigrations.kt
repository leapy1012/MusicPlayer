package gd.app.musicplayer.data.db

import android.database.Cursor
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {

    private val migration3000To3001 = object : Migration(3000, 3001) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE musictbl ADD sort INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val migration3001To3002 = object : Migration(3001, 3002) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE musictbl ADD lrc_offset INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val migration3002To3003 = object : Migration(3002, 3003) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE musictbl ADD hide_time INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val migration3003To3004 = object : Migration(3003, 3004) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE musictbl ADD date_modified INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE musictbl SET date_modified = date")
        }
    }

    private val migration3004To3005 = object : Migration(3004, 3005) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE musictbl ADD folder_name TEXT")

            db.query("SELECT _id, folder_path FROM musictbl").use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow("_id")
                val folderPathIndex = cursor.getColumnIndexOrThrow("folder_path")

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val folderPath = cursor.getString(folderPathIndex)
                    val folderName = extractFolderName(folderPath)
                    db.execSQL(
                        "UPDATE musictbl SET folder_name = ? WHERE _id = ?",
                        arrayOf<Any?>(folderName, id)
                    )
                }
            }
        }
    }

    private val migration3005To3006 = object : Migration(3005, 3006) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                DELETE FROM music_playlist
                WHERE _id NOT IN (
                    SELECT MIN(_id)
                    FROM music_playlist
                    GROUP BY m_id, p_id
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS index_music_playlist_m_id_p_id
                ON music_playlist(m_id, p_id)
                """.trimIndent()
            )
        }
    }

    val all = arrayOf(
        migration3000To3001,
        migration3001To3002,
        migration3002To3003,
        migration3003To3004,
        migration3004To3005,
        migration3005To3006
    )

    private fun extractFolderName(folderPath: String?): String? {
        if (folderPath.isNullOrBlank()) return folderPath
        return folderPath.trimEnd('/', '\\')
            .substringAfterLast('/')
            .substringAfterLast('\\')
    }
}

internal fun Cursor.getIntOrNull(index: Int): Int? =
    if (isNull(index)) null else getInt(index)

internal fun Cursor.getLongOrNull(index: Int): Long? =
    if (isNull(index)) null else getLong(index)
