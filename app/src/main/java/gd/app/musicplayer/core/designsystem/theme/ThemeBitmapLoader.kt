package gd.app.musicplayer.core.designsystem.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import gd.app.musicplayer.data.local.preference.ThemeSettingPreferenceStore
import gd.app.musicplayer.core.common.util.FastBlur
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeBitmapLoader @Inject constructor() {

    @Volatile
    private var cachedThemeBitmap: Bitmap? = null

    @Volatile
    private var cachedThemeBitmapKey: String? = null

    @Volatile
    private var cachedBlurBackgroundBitmap: Bitmap? = null

    @Volatile
    private var cachedBlurBackgroundBitmapKey: String? = null

    fun loadBitmap(
        context: Context,
        imageName: String,
        blurRadius: Int
    ): Bitmap? {
        val cacheKey = "$imageName#$blurRadius"

        cachedThemeBitmap?.takeIf {
            cachedThemeBitmapKey == cacheKey && !it.isRecycled
        }?.let { return it }

        val original = loadSourceBitmap(context, imageName) ?: return null

        val rendered = if (blurRadius <= 0) {
            original
        } else {
            FastBlur.blur(original, blurRadius, true)
        }

        cachedThemeBitmapKey = cacheKey
        cachedThemeBitmap = rendered

        return rendered
    }

    fun loadBlurBackgroundBitmap(
        context: Context,
        imageName: String,
        blurRadius: Int = DEFAULT_BLUR_BACKGROUND_RADIUS
    ): Bitmap? {
        val overlayColor = 855638016 // #33000000
        val cacheKey = "$imageName#$blurRadius#$overlayColor"

        cachedBlurBackgroundBitmap?.takeIf {
            cachedBlurBackgroundBitmapKey == cacheKey && !it.isRecycled
        }?.let { return it }

        val original = loadSourceBitmap(context, imageName) ?: return null

        val blurred = FastBlur.blur(
            original,
            blurRadius,
            true
        ) ?: return null

        val rendered = blurred.copy(Bitmap.Config.ARGB_8888, true)

        val canvas = Canvas(rendered)
        canvas.drawColor(overlayColor)

        cachedBlurBackgroundBitmapKey = cacheKey
        cachedBlurBackgroundBitmap = rendered

        return rendered
    }

    fun clearCache() {
        cachedThemeBitmap = null
        cachedThemeBitmapKey = null
        cachedBlurBackgroundBitmap = null
        cachedBlurBackgroundBitmapKey = null
    }

    private fun loadSourceBitmap(
        context: Context,
        imageName: String
    ): Bitmap? {
        return openThemeInputStream(context, imageName)?.use(BitmapFactory::decodeStream)
            ?: runCatching {
                context.assets.open(ThemeSettingPreferenceStore.DEFAULT_THEME_IMAGE)
                    .use(BitmapFactory::decodeStream)
            }.getOrNull()
    }

    private fun openThemeInputStream(
        context: Context,
        imageName: String
    ) = runCatching {
        if (File(imageName).isAbsolute) {
            File(imageName).takeIf(File::exists)?.inputStream()
        } else {
            context.assets.open(imageName)
        }
    }.getOrNull()

    private companion object {
        const val DEFAULT_BLUR_BACKGROUND_RADIUS = 80
    }
}