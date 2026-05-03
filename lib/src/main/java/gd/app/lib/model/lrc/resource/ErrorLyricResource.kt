package gd.app.lib.model.lrc.resource

import java.io.BufferedReader
import java.io.IOException

class ErrorLyricResource : LyricResource {

    override fun openReader(): BufferedReader {
        throw IOException("ErrorLyricResource can not open file!")
    }
}