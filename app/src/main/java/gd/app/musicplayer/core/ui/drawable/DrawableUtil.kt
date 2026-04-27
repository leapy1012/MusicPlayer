package gd.app.musicplayer.core.ui.drawable

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.graphics.drawable.shapes.RectShape
import android.graphics.drawable.shapes.RoundRectShape
import android.view.Gravity
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.scale
import gd.app.musicplayer.core.ui.extension.appContainer
import gd.app.musicplayer.util.ThemePreferenceOps
import gd.app.musicplayer.util.FastBlur
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object DrawableUtil {
    @Volatile
    private var cachedThemeBitmap: Bitmap? = null

    @Volatile
    private var cachedThemeBitmapKey: String? = null

    @Volatile
    private var cachedBlurBackgroundBitmap: Bitmap? = null

    @Volatile
    private var cachedBlurBackgroundBitmapKey: String? = null

    @JvmStatic
    fun ovalRipple(fillColor: Int, rippleColor: Int): Drawable {
        val content = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fillColor)
        }
        return RippleDrawable(
            ColorStateList.valueOf(rippleColor),
            content,
            ShapeDrawable(OvalShape())
        )
    }

    @JvmStatic
    fun gradientDrawable(radius: Float, fillColor: Int) : Drawable {
        return GradientDrawable().apply {
            cornerRadius = radius
            setColor(fillColor)
        }
    }

    @JvmStatic
    fun roundedRipple(fillColor: Int, rippleColor: Int, radius: Float): Drawable {
        val gradientDrawable = GradientDrawable().apply {
            cornerRadius = radius
            setColor(fillColor)
        }
        val maskRadii = FloatArray(8) { radius }
        return RippleDrawable(
            ColorStateList.valueOf(rippleColor),
            gradientDrawable,
            ShapeDrawable(RoundRectShape(maskRadii, null, null))
        )
    }

    @JvmStatic
    fun outlinedRoundedRipple(
        cornerRadius: Int,
        strokeWidth: Int,
        strokeColor: Int,
        rippleColor: Int
    ): Drawable {
        return outlinedRoundedRipple(
            cornerRadius = cornerRadius,
            strokeWidth = strokeWidth,
            strokeColor = strokeColor,
            fillColor = 0,
            rippleColor = rippleColor
        )
    }

    @JvmStatic
    fun outlinedRoundedRipple(
        cornerRadius: Int,
        strokeWidth: Int,
        strokeColor: Int,
        fillColor: Int,
        rippleColor: Int
    ): Drawable {
        val radius = cornerRadius.toFloat()
        val content = GradientDrawable().apply {
            setStroke(strokeWidth, strokeColor)
            this.cornerRadius = radius
            if (fillColor != 0) {
                setColor(fillColor)
            }
        }
        val maskRadii = FloatArray(8) { radius }
        return RippleDrawable(
            ColorStateList.valueOf(rippleColor),
            content,
            ShapeDrawable(RoundRectShape(maskRadii, null, null))
        )
    }

    @JvmStatic
    fun roundedFill(cornerRadius: Float, fillColor: Int): Drawable {
        return GradientDrawable().apply {
            this.cornerRadius = cornerRadius
            setColor(fillColor)
        }
    }

    @JvmStatic
    fun roundedProgress(
        backgroundColor: Int,
        progressColor: Int,
        cornerRadius: Int
    ): Drawable {
        val orientation = GradientDrawable.Orientation.LEFT_RIGHT
        val background = GradientDrawable(orientation, intArrayOf(backgroundColor, backgroundColor)).apply {
            this.cornerRadius = cornerRadius.toFloat()
        }
        val progress = GradientDrawable(orientation, intArrayOf(progressColor, progressColor)).apply {
            this.cornerRadius = cornerRadius.toFloat()
        }
        return layeredProgress(
            background,
            ClipDrawable(progress, Gravity.START, ClipDrawable.HORIZONTAL)
        )
    }

    @JvmStatic
    fun layeredProgress(background: Drawable, progress: Drawable): Drawable {
        return LayerDrawable(arrayOf(background, progress)).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
        }
    }

    @JvmStatic
    fun rectRipple(fillColor: Int, rippleColor: Int): Drawable {
        return RippleDrawable(
            ColorStateList.valueOf(rippleColor),
            fillColor.toDrawable(),
            ShapeDrawable(RectShape())
        )
    }

    @JvmStatic
    fun tinted(drawable: Drawable?, tintColor: Int): Drawable? {
        return drawable?.let {
            DrawableCompat.wrap(it).mutate().also { wrapped ->
                DrawableCompat.setTint(wrapped, tintColor)
            }
        }
    }
    fun loadBitmap(context: Context): Bitmap? {
        val preferenceUtil = context.appContainer.preferenceUtil
        return loadBitmap(
            context = context,
            imageName = preferenceUtil.getThemeImageName(),
            blurRadius = preferenceUtil.getThemeBlur()
        )
    }

    fun loadBitmap(context: Context, imageName: String, blurRadius: Int): Bitmap? {
        val cacheKey = "$imageName#$blurRadius"

        // Dialogs and sheets request this background repeatedly. Cache the rendered bitmap so
        // opening a dialog doesn't keep decoding the same asset and blurring it again.
        cachedThemeBitmap?.takeIf { cachedThemeBitmapKey == cacheKey && !it.isRecycled }?.let {
            return it
        }

        val original = loadSourceBitmap(context, imageName) ?: return null

        val rendered = if (blurRadius <= 0) original else blur(original, blurRadius)
        cachedThemeBitmapKey = cacheKey
        cachedThemeBitmap = rendered
        return rendered
    }

    fun loadBlurBackgroundBitmap(context: Context): Bitmap? {
        val preferenceUtil = context.appContainer.preferenceUtil
        return loadBlurBackgroundBitmap(context, preferenceUtil.getThemeImageName())
    }

    fun loadBlurBackgroundBitmap(context: Context, imageName: String): Bitmap? {
        val cacheKey = "$imageName#80"

        cachedBlurBackgroundBitmap?.takeIf {
            cachedBlurBackgroundBitmapKey == cacheKey && !it.isRecycled
        }?.let { return it }

        val original = loadSourceBitmap(context, imageName) ?: return null

        val rendered = FastBlur.blur(original, 80, true)
        cachedBlurBackgroundBitmapKey = cacheKey
        cachedBlurBackgroundBitmap = rendered
        return rendered
    }

    private fun loadSourceBitmap(context: Context, imageName: String): Bitmap? {
        return openThemeInputStream(context, imageName)?.use(BitmapFactory::decodeStream)
            ?: runCatching {
                context.assets.open(ThemePreferenceOps.DEFAULT_THEME_IMAGE).use(BitmapFactory::decodeStream)
            }.getOrNull()
    }

    private fun openThemeInputStream(context: Context, imageName: String) =
        runCatching {
            if (File(imageName).isAbsolute) {
                File(imageName).takeIf(File::exists)?.inputStream()
            } else {
                context.assets.open(imageName)
            }
        }.getOrNull()

    fun blur(source: Bitmap?, radius: Int): Bitmap? {
        if (source == null) return null
        if (radius < 1) {
            return source.copy(Bitmap.Config.ARGB_8888, true)
        }

        val bitmap = source.copy(Bitmap.Config.ARGB_8888, true)
        val width = bitmap.width
        val height = bitmap.height

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val widthMinusOne = width - 1
        val heightMinusOne = height - 1
        val pixelCount = width * height
        val div = radius + radius + 1

        val red = IntArray(pixelCount)
        val green = IntArray(pixelCount)
        val blue = IntArray(pixelCount)
        val minValues = IntArray(max(width, height))

        var divSum = (div + 1) shr 1
        divSum *= divSum
        val divisionLookup = IntArray(256 * divSum)
        for (i in divisionLookup.indices) {
            divisionLookup[i] = i / divSum
        }

        val stack = Array(div) { IntArray(3) }
        val radiusPlusOne = radius + 1

        var pixelIndex = 0
        var rowStart = 0

        for (y in 0 until height) {
            var redInSum = 0
            var greenInSum = 0
            var blueInSum = 0
            var redOutSum = 0
            var greenOutSum = 0
            var blueOutSum = 0
            var redSum = 0
            var greenSum = 0
            var blueSum = 0

            for (i in -radius..radius) {
                val pixel = pixels[pixelIndex + min(widthMinusOne, max(i, 0))]
                val stackEntry = stack[i + radius]
                stackEntry[0] = pixel shr 16 and 0xff
                stackEntry[1] = pixel shr 8 and 0xff
                stackEntry[2] = pixel and 0xff

                val weight = radiusPlusOne - abs(i)
                redSum += stackEntry[0] * weight
                greenSum += stackEntry[1] * weight
                blueSum += stackEntry[2] * weight

                if (i > 0) {
                    redInSum += stackEntry[0]
                    greenInSum += stackEntry[1]
                    blueInSum += stackEntry[2]
                } else {
                    redOutSum += stackEntry[0]
                    greenOutSum += stackEntry[1]
                    blueOutSum += stackEntry[2]
                }
            }

            var stackPointer = radius

            for (x in 0 until width) {
                red[pixelIndex] = divisionLookup[redSum]
                green[pixelIndex] = divisionLookup[greenSum]
                blue[pixelIndex] = divisionLookup[blueSum]

                redSum -= redOutSum
                greenSum -= greenOutSum
                blueSum -= blueOutSum

                val stackStart = stackPointer - radius + div
                val stackEntry = stack[stackStart % div]

                redOutSum -= stackEntry[0]
                greenOutSum -= stackEntry[1]
                blueOutSum -= stackEntry[2]

                if (y == 0) {
                    minValues[x] = min(x + radius + 1, widthMinusOne)
                }

                val pixel = pixels[rowStart + minValues[x]]
                stackEntry[0] = pixel shr 16 and 0xff
                stackEntry[1] = pixel shr 8 and 0xff
                stackEntry[2] = pixel and 0xff

                redInSum += stackEntry[0]
                greenInSum += stackEntry[1]
                blueInSum += stackEntry[2]

                redSum += redInSum
                greenSum += greenInSum
                blueSum += blueInSum

                stackPointer = (stackPointer + 1) % div
                val nextStackEntry = stack[stackPointer]

                redOutSum += nextStackEntry[0]
                greenOutSum += nextStackEntry[1]
                blueOutSum += nextStackEntry[2]

                redInSum -= nextStackEntry[0]
                greenInSum -= nextStackEntry[1]
                blueInSum -= nextStackEntry[2]

                pixelIndex++
            }
            rowStart += width
        }

        for (x in 0 until width) {
            var redInSum = 0
            var greenInSum = 0
            var blueInSum = 0
            var redOutSum = 0
            var greenOutSum = 0
            var blueOutSum = 0
            var redSum = 0
            var greenSum = 0
            var blueSum = 0
            var yOffset = -radius * width

            for (i in -radius..radius) {
                pixelIndex = max(0, yOffset) + x
                val stackEntry = stack[i + radius]
                stackEntry[0] = red[pixelIndex]
                stackEntry[1] = green[pixelIndex]
                stackEntry[2] = blue[pixelIndex]

                val weight = radiusPlusOne - abs(i)
                redSum += red[pixelIndex] * weight
                greenSum += green[pixelIndex] * weight
                blueSum += blue[pixelIndex] * weight

                if (i > 0) {
                    redInSum += stackEntry[0]
                    greenInSum += stackEntry[1]
                    blueInSum += stackEntry[2]
                } else {
                    redOutSum += stackEntry[0]
                    greenOutSum += stackEntry[1]
                    blueOutSum += stackEntry[2]
                }

                if (i < heightMinusOne) {
                    yOffset += width
                }
            }

            pixelIndex = x
            var stackPointer = radius

            for (y in 0 until height) {
                pixels[pixelIndex] = (pixels[pixelIndex] and -0x1000000) or
                        (divisionLookup[redSum] shl 16) or
                        (divisionLookup[greenSum] shl 8) or
                        divisionLookup[blueSum]

                redSum -= redOutSum
                greenSum -= greenOutSum
                blueSum -= blueOutSum

                val stackStart = stackPointer - radius + div
                val stackEntry = stack[stackStart % div]

                redOutSum -= stackEntry[0]
                greenOutSum -= stackEntry[1]
                blueOutSum -= stackEntry[2]

                if (x == 0) {
                    minValues[y] = min(y + radiusPlusOne, heightMinusOne) * width
                }

                val index = x + minValues[y]
                stackEntry[0] = red[index]
                stackEntry[1] = green[index]
                stackEntry[2] = blue[index]

                redInSum += stackEntry[0]
                greenInSum += stackEntry[1]
                blueInSum += stackEntry[2]

                redSum += redInSum
                greenSum += greenInSum
                blueSum += blueInSum

                stackPointer = (stackPointer + 1) % div
                val nextStackEntry = stack[stackPointer]

                redOutSum += nextStackEntry[0]
                greenOutSum += nextStackEntry[1]
                blueOutSum += nextStackEntry[2]

                redInSum -= nextStackEntry[0]
                greenInSum -= nextStackEntry[1]
                blueInSum -= nextStackEntry[2]

                pixelIndex += width
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun decodeOriginalBitmap(imageBytes: ByteArray): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
    }

    private fun decodeScaledBitmap(
        imageBytes: ByteArray,
        requestedWidth: Int,
        requestedHeight: Int
    ): Bitmap? {

        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, boundsOptions)

        val decodeOptions = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = calculateInSampleSize(
                options = boundsOptions,
                requestedWidth = requestedWidth,
                requestedHeight = requestedHeight
            )
        }
        val decodedBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOptions)

        return decodedBitmap?.scale(requestedWidth, requestedHeight)
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        requestedWidth: Int,
        requestedHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > requestedHeight || width > requestedWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= requestedHeight &&
                halfWidth / inSampleSize >= requestedWidth
            ) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    fun createActivityBackground(context: Context): BitmapDrawable {
        val overlayColor = context.appContainer.preferenceUtil.getThemeOverlayColor()
        return OverlayCenterCropDrawable(context.resources, loadBitmap(context), overlayColor)
    }
}

