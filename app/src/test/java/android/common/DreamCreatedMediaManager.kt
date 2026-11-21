// Leapy_tts added begin: Test-only framework stand-in; never packaged into the application.
package android.common

import android.content.Context
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import java.io.File

object DreamCreatedMediaManager {
    val events = mutableListOf<String>()
    var failPrepare = false
    var preparedBytes = byteArrayOf()
    var publishedPath: String? = null
    fun reset() { events.clear(); failPrepare = false; preparedBytes = byteArrayOf(); publishedPath = null }
    private fun path(context: Context, uri: Uri): String = context.contentResolver.query(
        uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)!!.use {
        check(it.moveToFirst()); it.getString(0)
    }
    @JvmStatic fun prepare(context: Context, uri: Uri, hint: String?): String? {
        events += "prepare"
        if (failPrepare) return null
        val file = File(path(context, uri))
        preparedBytes = file.readBytes()
        check(preparedBytes.isNotEmpty())
        file.appendText("TEST_SIGN_FOOTER")
        context.contentResolver.update(uri, ContentValues().apply {
            put(MediaStore.MediaColumns.SIZE, file.length())
        }, null, null)
        return file.absolutePath
    }
    @JvmStatic fun onPublished(context: Context, uri: Uri, preparedPath: String) {
        publishedPath = path(context, uri)
        check(File(publishedPath!!).isFile)
        events += "notify"
    }
}
// Leapy_tts end
