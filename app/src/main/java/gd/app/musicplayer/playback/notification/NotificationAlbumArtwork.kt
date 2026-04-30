package gd.app.musicplayer.playback.notification

import android.graphics.Bitmap
import androidx.palette.graphics.Palette

data class NotificationAlbumArtwork(
    val originalBitmap: Bitmap?,
    val displayBitmap: Bitmap?,
    val palette: Palette?,
) {

    val hasDisplayBitmap: Boolean
        get() = displayBitmap?.isRecycled == false

    companion object {
        val EMPTY = NotificationAlbumArtwork(
            originalBitmap = null,
            displayBitmap = null,
            palette = null,
        )
    }
}