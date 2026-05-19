package gd.app.musicplayer.ui.lyrics

import java.io.File

data class LyricFile(
    val path: String,
    val title: String,
    val folder: String
) {
    constructor(file: File) : this(
        path = file.absolutePath,
        title = file.name,
        folder = file.parent.orEmpty()
    )
}
