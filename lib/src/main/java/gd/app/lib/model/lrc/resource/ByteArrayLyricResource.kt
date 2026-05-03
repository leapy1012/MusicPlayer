package gd.app.lib.model.lrc.resource

import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader

class ByteArrayLyricResource(
    private val data: ByteArray,
    private val charsetName: String
) : LyricResource {

    override fun openReader(): BufferedReader {
        return BufferedReader(
            InputStreamReader(
                ByteArrayInputStream(data),
                charsetName
            )
        )
    }
}