package gd.app.lib.model.lrc.resource

import java.io.BufferedReader

interface LyricResource {
    fun openReader(): BufferedReader
}