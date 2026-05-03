package gd.app.musicplayer.core.ui.drawable

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toDrawable
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.dpToPx

object DialogBackgroundFactory {

    // Theme types from project behavior
    const val THEME_TYPE_PICTURE = 2
    const val THEME_TYPE_NIGHT = 99

    private const val COLOR_PICTURE_FALLBACK = -394759  // -394759
    private const val COLOR_PICTURE_OVERLAY = 0xBF2A3139.toInt()   // -1087753927

    /**
     * Equivalent to m4.a.l()
     */
    @JvmStatic
    fun fallbackDefaultDialogBackground(): Drawable = Color.WHITE.toDrawable()

    /**
     * Equivalent to p7.g.l() -> @drawable/popup_bg_night
     */
    @JvmStatic
    fun nightDialogBackground(context: Context): Drawable {
        return requireNotNull(
            androidx.appcompat.content.res.AppCompatResources.getDrawable(
                context,
                R.drawable.popup_bg_night
            )
        )
    }

    /**
     * Equivalent to p7.j.l() -> new k(z(), 12dp)
     *
     * @param blurredThemeBitmap picture theme blur bitmap (p7.j.f13629d)
     */
    @JvmStatic
    fun pictureDialogBackground(
        context: Context,
        blurredThemeBitmap: Bitmap?
    ): Drawable {
        val base: Drawable = if (blurredThemeBitmap == null) {
            COLOR_PICTURE_FALLBACK.toDrawable()
        } else {
            ScaledOverlayDrawable(blurredThemeBitmap.toDrawable(context.resources)).apply {
                setForegroundOverlayColor(COLOR_PICTURE_OVERLAY)
            }
        }

        return RoundedMaskDrawable(base, context.dpToPx(12f).toFloat())
    }
}
