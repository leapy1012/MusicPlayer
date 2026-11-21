// Leapy_tts added begin: Check the real firmware wrapper before importing audio metadata.
package gd.app.musicplayer.core.media

import android.util.Log
import com.Legal.Java.Interface
import java.io.File
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

    fun inspect(path: String): Decision {
        val file = File(path)
        if (!file.isAbsolute || !file.isFile) return Decision.MISSING
        // This is a required platform API. A framework/ABI error must propagate,
        // not silently turn every library item into an unavailable/filtered result.
        val result = api.check(path)
        if (result == api.nativeResult || result == api.selfResult) return Decision.ALLOWED
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
