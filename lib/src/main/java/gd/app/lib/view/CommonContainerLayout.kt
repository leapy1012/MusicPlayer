package gd.app.lib.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.ViewOutlineProvider
import kotlin.math.abs

class CommonContainerLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConfigurationFrameLayout(context, attrs, defStyleAttr) {

    private var cornerRadii: FloatArray? = null
    private var cornerMode: Int = CORNER_MODE_NONE
    private val clipPath = Path()

    override fun draw(canvas: Canvas) {
        if (cornerMode == CORNER_MODE_CUSTOM_PATH && !clipPath.isEmpty) {
            canvas.save()
            canvas.clipPath(clipPath)
        }
        super.draw(canvas)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)

        val radii = cornerRadii
        if (cornerMode == CORNER_MODE_CUSTOM_PATH && radii != null) {
            rebuildClipPath(radii, width, height)
        }
    }

    fun setRadiusArray(radii: FloatArray) {
        cornerRadii = radii.copyOf()
        cornerMode = resolveCornerMode(radii)

        when (cornerMode) {
            CORNER_MODE_UNIFORM_OUTLINE -> {
                clipToOutline = true
                outlineProvider = RoundedOutlineProvider(cornerRadii!![0])
                setLayerType(LAYER_TYPE_HARDWARE, null)
            }

            CORNER_MODE_CUSTOM_PATH -> {
                clipToOutline = false
                outlineProvider = ViewOutlineProvider.BACKGROUND
                setLayerType(LAYER_TYPE_SOFTWARE, null)
                rebuildClipPath(cornerRadii!!, width, height)
            }

            else -> {
                clipToOutline = false
                outlineProvider = ViewOutlineProvider.BACKGROUND
                setLayerType(LAYER_TYPE_HARDWARE, null)
            }
        }

        postInvalidate()
    }

    private fun resolveCornerMode(radii: FloatArray): Int {
        val firstRadius = radii[0]
        var allZero = true
        var allEqual = true

        for (radius in radii) {
            if (allZero && radius > 0f) {
                allZero = false
            }
            if (allEqual && !floatsEqual(radius, firstRadius)) {
                allEqual = false
            }
        }

        return when {
            allZero -> CORNER_MODE_NONE
            allEqual -> CORNER_MODE_UNIFORM_OUTLINE
            else -> CORNER_MODE_CUSTOM_PATH
        }
    }

    private fun rebuildClipPath(radii: FloatArray, width: Int, height: Int) {
        clipPath.reset()

        if (width <= 0 || height <= 0) return

        clipPath.addRoundRect(
            RectF(
                0f,
                0f,
                width.toFloat(),
                height.toFloat()
            ),
            radii,
            Path.Direction.CW
        )
    }

    private fun floatsEqual(a: Float, b: Float, epsilon: Float = 0.001f): Boolean {
        return abs(a - b) < epsilon
    }

    private companion object {
        const val CORNER_MODE_NONE = 0
        const val CORNER_MODE_UNIFORM_OUTLINE = 1
        const val CORNER_MODE_CUSTOM_PATH = 2
    }
}