package gd.app.musicplayer.playback.restore

import android.content.Context
import android.net.Uri
import gd.app.musicplayer.domain.model.Music
import java.io.File
import kotlinx.coroutines.CancellationException

object PlaybackSourceValidator {
    fun isReadable(context: Context, music: Music): Boolean {
        val data = music.data?.takeIf { it.isNotBlank() } ?: return false
        return try {
            val uri = Uri.parse(data)
            when (uri.scheme?.lowercase()) {
                null, "" -> File(data).let { it.isFile && it.canRead() }
                "file" -> uri.path?.let { File(it).let { f -> f.isFile && f.canRead() } } == true
                "content" -> context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } == true
                else -> false
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    }
}
