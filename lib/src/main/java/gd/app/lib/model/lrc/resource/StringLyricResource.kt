package gd.app.lib.model.lrc.resource

import java.io.BufferedReader
import java.io.StringReader

class StringLyricResource(
    private val text: String
) : LyricResource {

    override fun openReader(): BufferedReader {
        return BufferedReader(StringReader(text))
    }
}