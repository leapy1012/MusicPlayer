package gd.app.musicplayer.util

import android.util.Log
import java.io.File

/** package: o8.a0 */
abstract class AppLogger {

    companion object {
        var isDebugLoggingEnabled: Boolean
        val isFileLoggingEnabled: Boolean
        val isSomeExtraLoggingFlagEnabled: Boolean

        init {
//            val config = q8.a.a()
//            isDebugLoggingEnabled = config.a()
//            isFileLoggingEnabled = config.b()
//            isSomeExtraLoggingFlagEnabled = config.c()
            isDebugLoggingEnabled = false
            isFileLoggingEnabled = false
            isSomeExtraLoggingFlagEnabled = false
        }

        // original: a
        @JvmStatic
        fun debug(tag: String, message: String) {
            if (isDebugLoggingEnabled) {
                Log.d(tag, message)
            }
        }

        // original: b
        @JvmStatic
        fun error(tag: String, message: String) {
            if (isDebugLoggingEnabled) {
                Log.e(tag, message)
            }
        }

        // original: c
        @JvmStatic
        fun error(tag: String, throwable: Throwable) {
            if (isDebugLoggingEnabled) {
//                Log.e(tag, t.a(throwable))
            }
        }

        // original: d
        @JvmStatic
        fun info(tag: String, message: String) {
            if (isDebugLoggingEnabled) {
                Log.i(tag, message)
            }
        }

        // original: e
        @JvmStatic
        fun errorAndWriteToFile(tag: String, message: String) {
            if (isDebugLoggingEnabled) {
                Log.e(tag, message)
            }

            if (isFileLoggingEnabled) {
//                val timestamp = s0.c(System.currentTimeMillis(), "yyyy-MM-dd HH:mm:ss.SSS")
//                val logLine = "$timestamp $tag $message\n"
//                w8.a.b().execute(WriteLogToFileTask(logLine))
            }
        }
    }

    class WriteLogToFileTask(
        private val logText: String
    ) : Runnable {

        override fun run() {
//            val logFile = File(c.f().h().externalCacheDir, "Llog.txt")
//            u.a(logFile.absolutePath, false)
//            v.l(logText, logFile, true)
        }
    }
}
