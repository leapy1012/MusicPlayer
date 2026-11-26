// Leapy_tts added begin: Check the real firmware wrapper before importing audio metadata.
package gd.app.musicplayer.core.media

import android.util.Log
import com.Legal.Java.Interface
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import javax.inject.Inject

class LegalAudioFilePolicy internal constructor(private val api: Api) {
    @Inject constructor() : this(FrameworkApi())

    internal interface Api {
        val nativeResult: Int
        val selfResult: Int
        fun check(path: String): Int
        fun delete(path: String)
    }

    enum class Decision { ALLOWED, REJECTED, MISSING }

    // Leapy_tts modified begin: Reuse successful checks only for the same on-disk file.
    private val acceptedFiles = mutableMapOf<String, FileVersion>()

    @Synchronized
    fun inspect(path: String, reuseAccepted: Boolean = false): Decision {
        val file = File(path)
        if (!file.isAbsolute || !file.isFile) {
            acceptedFiles.remove(path)
            return Decision.MISSING
        }
        val version = file.version()
        if (reuseAccepted && version != null && acceptedFiles[path] == version) {
            return Decision.ALLOWED
        }
        acceptedFiles.remove(path)
        // This is a required platform API. A framework/ABI error must propagate,
        // not silently turn every library item into an unavailable/filtered result.
        val result = api.check(path)
        if (result == api.nativeResult || result == api.selfResult) {
            if (version != null && file.version() == version) {
                acceptedFiles[path] = version
            }
            return Decision.ALLOWED
        }
        // A check error never authorizes deletion. Only an actual rejected verdict does.
        try {
            api.delete(path)
            if (file.exists()) Log.w(TAG, "Rejected audio could not be deleted: $path (result=$result)")
            else Log.i(TAG, "Deleted rejected audio: $path (result=$result)")
        } catch (error: Exception) {
            Log.e(TAG, "Rejected audio could not be deleted: $path", error)
        }
        return Decision.REJECTED
    }

    private data class FileVersion(
        val key: Any?,
        val size: Long,
        val modified: FileTime,
        val created: FileTime
    )

    private fun File.version(): FileVersion? = try {
        val attrs = Files.readAttributes(toPath(), BasicFileAttributes::class.java)
        FileVersion(attrs.fileKey(), attrs.size(), attrs.lastModifiedTime(), attrs.creationTime())
    } catch (error: IOException) {
        null
    }
    // Leapy_tts end

    private class FrameworkApi : Api {
        override val nativeResult get() = Interface.Legal_NAT
        override val selfResult get() = Interface.Legal_SELF
        override fun check(path: String) = Interface.isLegalFile(path)
        // deleteLegalFile(Context, path) is empty in the supplied framework wrapper.
        override fun delete(path: String) = Interface.deleteFile(path)
    }

    private companion object { const val TAG = "LegalAudioFilePolicy" }
}
// Leapy_tts end
