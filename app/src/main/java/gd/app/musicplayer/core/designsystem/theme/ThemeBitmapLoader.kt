package gd.app.musicplayer.core.designsystem.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import gd.app.musicplayer.core.common.extension.screenHeight
import gd.app.musicplayer.core.common.extension.screenWidth
import gd.app.musicplayer.core.datastore.ThemeSettingPreferenceStore
import gd.app.musicplayer.core.common.util.FastBlur
import java.io.InputStream
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

        val (targetWidth, targetHeight) = resolveTargetSize(
            context = context,
            blurRadius = blurRadius
        )
        val original = loadSourceBitmap(
            context = context,
            imageName = imageName,
            targetWidth = targetWidth,
            targetHeight = targetHeight
        ) ?: return null

        val rendered = if (blurRadius <= 0) {
            original
        } else {
            FastBlur.blur(original, blurRadius, true)
        }

        Log.d(
            TAG,
            "loadBitmap image=$imageName blur=$blurRadius size=${rendered?.width}x${rendered?.height}"
        )

        cachedThemeBitmapKey = cacheKey
        cachedThemeBitmap = rendered

        return rendered
    }

    fun loadBlurBackgroundBitmap(
        context: Context,
        imageName: String,
        blurRadius: Int = DEFAULT_BLUR_BACKGROUND_RADIUS
    ): Bitmap? {
        val cacheKey = "$imageName#$blurRadius"

        cachedBlurBackgroundBitmap?.takeIf {
            cachedBlurBackgroundBitmapKey == cacheKey && !it.isRecycled
        }?.let { return it }

        val (targetWidth, targetHeight) = resolveTargetSize(
            context = context,
            blurRadius = blurRadius
        )
        val original = loadSourceBitmap(
            context = context,
            imageName = imageName,
            targetWidth = targetWidth,
            targetHeight = targetHeight
        ) ?: return null

        val blurred = FastBlur.blur(
            original,
            blurRadius,
            true
        ) ?: return null

        Log.d(
            TAG,
            "loadBlurBackgroundBitmap image=$imageName blur=$blurRadius src=${original.width}x${original.height} out=${blurred.width}x${blurred.height}"
        )

        cachedBlurBackgroundBitmapKey = cacheKey
        cachedBlurBackgroundBitmap = blurred

        return blurred
    }

    fun clearCache() {
        cachedThemeBitmap = null
        cachedThemeBitmapKey = null
        cachedBlurBackgroundBitmap = null
        cachedBlurBackgroundBitmapKey = null
    }

    private fun loadSourceBitmap(
        context: Context,
        imageName: String,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        val primary = decodeScaledBitmap(
            context = context,
            imageName = imageName,
            targetWidth = targetWidth,
            targetHeight = targetHeight
        )

        if (primary != null) {
            Log.d(
                TAG,
                "loadSourceBitmap primary image=$imageName size=${primary.width}x${primary.height}"
            )
            return primary
        }

        val fallback = runCatching {
            decodeScaledBitmap(
                context = context,
                imageName = ThemeSettingPreferenceStore.DEFAULT_THEME_IMAGE,
                targetWidth = targetWidth,
                targetHeight = targetHeight
            )
        }.getOrNull()

        Log.w(
            TAG,
            "loadSourceBitmap fallback image=$imageName -> ${ThemeSettingPreferenceStore.DEFAULT_THEME_IMAGE}, fallbackSize=${fallback?.width}x${fallback?.height}"
        )
        return fallback
    }

    private fun openThemeInputStream(
        context: Context,
        imageName: String
    ): InputStream? {
        val normalized = imageName.trim()
        if (normalized.isEmpty()) return null

        return runCatching {
            when {
                normalized.startsWith("/android_asset/") -> {
                    context.assets.open(normalized.removePrefix("/android_asset/"))
                }

                normalized.startsWith("android_asset/") -> {
                    context.assets.open(normalized.removePrefix("android_asset/"))
                }

                normalized.startsWith("/") -> {
                    File(normalized).takeIf(File::exists)?.inputStream()
                }

                else -> {
                    context.assets.open(normalized)
                }
            }
        }.getOrNull()
    }

    private fun decodeScaledBitmap(
        context: Context,
        imageName: String,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        val safeTargetWidth = targetWidth.coerceAtLeast(10)
        val safeTargetHeight = targetHeight.coerceAtLeast(10)

        val sourceBytes = openThemeInputStream(context, imageName)?.use { it.readBytes() } ?: return null

        // Some WEBP variants decode unreliably through decodeStream on specific devices.
        // Using byte-array decoding matches behavior more consistently.
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, boundsOptions)

        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
            Log.w(TAG, "decodeScaledBitmap bounds failed image=$imageName bytes=${sourceBytes.size}")
            return null
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(
                sourceWidth = boundsOptions.outWidth,
                sourceHeight = boundsOptions.outHeight,
                targetWidth = safeTargetWidth,
                targetHeight = safeTargetHeight
            )
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, decodeOptions)
        if (decoded == null) {
            Log.w(TAG, "decodeScaledBitmap decode failed image=$imageName sample=${decodeOptions.inSampleSize}")
            return null
        }

        if (decoded.width <= safeTargetWidth && decoded.height <= safeTargetHeight) {
            return decoded
        }

        val scale = minOf(
            safeTargetWidth.toFloat() / decoded.width.toFloat(),
            safeTargetHeight.toFloat() / decoded.height.toFloat()
        ).coerceAtMost(1f)

        val outWidth = (decoded.width * scale).toInt().coerceAtLeast(1)
        val outHeight = (decoded.height * scale).toInt().coerceAtLeast(1)

        if (outWidth == decoded.width && outHeight == decoded.height) {
            return decoded
        }

        return Bitmap.createScaledBitmap(decoded, outWidth, outHeight, true).also { scaled ->
            if (scaled !== decoded) {
                decoded.recycle()
            }
        }
    }

    private fun calculateInSampleSize(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Int {
        var sampleSize = 1

        while (sourceWidth / sampleSize > targetWidth * 2 ||
            sourceHeight / sampleSize > targetHeight * 2
        ) {
            sampleSize *= 2
        }

        return sampleSize.coerceAtLeast(1)
    }

    private fun resolveTargetSize(
        context: Context,
        blurRadius: Int
    ): Pair<Int, Int> {
        val baseWidth = context.screenWidth.toFloat().coerceAtLeast(1f)
        val baseHeight = context.screenHeight.toFloat().coerceAtLeast(1f)

        var targetWidth = baseWidth.toInt()
        var targetHeight = baseHeight.toInt()

        if (blurRadius > 0) {
            targetWidth = (
                maxOf(
                    80f,
                    baseWidth - (baseWidth * blurRadius / 50f)
                ).toInt() / 40
                ) * 40

            targetHeight = ((targetWidth / baseWidth) * baseHeight).toInt()
        }

        return targetWidth.coerceAtLeast(10) to targetHeight.coerceAtLeast(10)
    }

    private companion object {
        const val DEFAULT_BLUR_BACKGROUND_RADIUS = 80
        const val TAG = "ThemeBitmapLoader"
    }
}
