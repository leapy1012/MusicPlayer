package gd.app.lib.model.lrc.resource

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import org.mozilla.universalchardet.UniversalDetector
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

// r6.c

class FileLyricResource(
    private val filePath: String,
    private val charsetName: String = UniversalDetector.detectCharset(File(filePath)) ?: "UTF-8",
    private val context: Context
) : LyricResource {

    override fun openReader(): BufferedReader {
        val file = File(filePath)

        if (file.exists() && !file.canRead()) {
            val applicationContext = context.applicationContext
            val contentUri = MediaStore.Files.getContentUri("external")

            applicationContext.contentResolver.query(
                contentUri,
                arrayOf("_id"),
                "_data=?",
                arrayOf(filePath),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val mediaId = cursor.getLong(0)

                    val uri = ContentUris.withAppendedId(
                        contentUri,
                        mediaId
                    )

                    return BufferedReader(
                        InputStreamReader(
                            applicationContext.contentResolver.openInputStream(uri),
                            charsetName
                        )
                    )
                }
            }
        }

        return BufferedReader(
            InputStreamReader(
                FileInputStream(file),
                charsetName
            )
        )
    }
}