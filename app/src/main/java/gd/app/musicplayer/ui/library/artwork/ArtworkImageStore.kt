package gd.app.musicplayer.ui.library.artwork

import android.content.Context
import java.io.File

internal object ArtworkImageStore {

    fun createManagedArtworkFile(context: Context): File {
        val outputFile = File(context.filesDir, "artwork/artwork_${System.currentTimeMillis()}.jpg")
        outputFile.parentFile?.mkdirs()
        return outputFile
    }
}
