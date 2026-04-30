package gd.app.lib.view.square

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import gd.app.lib.view.translucent.TranslucentBarFrameLayout
import gd.app.lib.R
import androidx.core.graphics.withClip

open class RoundedCornerFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TranslucentBarFrameLayout(context, attrs) {

    private val clipPath = Path()

    private var cornerRadii: FloatArray = FloatArray(8)
    private var clipMode: ClipMode = ClipMode.None

    init {
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(
                attrs,
                R.styleable.RoundedCornerFrameLayout
            )

            try {
                if (typedArray.hasValue(R.styleable.RoundedCornerFrameLayout_corner_radius)) {
                    val radius = typedArray.getDimension(
                        R.styleable.RoundedCornerFrameLayout_corner_radius,
                        0f
                    )

                    setRadiusArray(floatArrayOf(radius))
                } else {
                    setRadiusArray(
                        floatArrayOf(
                            typedArray.getDimension(
                                R.styleable.RoundedCornerFrameLayout_corner_radiusTopLeft,
                                0f
                            ),
                            typedArray.getDimension(
                                R.styleable.RoundedCornerFrameLayout_corner_radiusTopRight,
                                0f
                            ),
                            typedArray.getDimension(
                                R.styleable.RoundedCornerFrameLayout_corner_radiusBottomRight,
                                0f
                            ),
                            typedArray.getDimension(
                                R.styleable.RoundedCornerFrameLayout_corner_radiusBottomLeft,
                                0f
                            )
                        )
                    )
                }
            } finally {
                typedArray.recycle()
            }
        }
    }

    override fun draw(canvas: Canvas) {
        if (clipMode == ClipMode.Path && !clipPath.isEmpty) {
            canvas.withClip(clipPath) {
                super.draw(canvas)
            }
        } else {
            super.draw(canvas)
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)

        if (clipMode == ClipMode.Path) {
            rebuildClipPath(width, height)
        }
    }

    fun setRadiusArray(radii: FloatArray) {
        cornerRadii = RadiusUtils.normalize(radii)
        clipMode = resolveClipMode(cornerRadii)

        when (clipMode) {
            ClipMode.None -> {
                clipToOutline = false
                outlineProvider = ViewOutlineProvider.BACKGROUND
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }

            ClipMode.UniformOutline -> {
                clipToOutline = true
                outlineProvider = RoundedOutlineProvider(cornerRadii[0])
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }

            ClipMode.Path -> {
                clipToOutline = false
                outlineProvider = ViewOutlineProvider.BACKGROUND
                setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                rebuildClipPath(width, height)
            }
        }

        postInvalidate()
    }

    fun setCornerRadius(radiusPx: Float) {
        setRadiusArray(floatArrayOf(radiusPx))
    }

    fun setCornerRadii(
        topLeft: Float,
        topRight: Float,
        bottomRight: Float,
        bottomLeft: Float
    ) {
        setRadiusArray(
            floatArrayOf(
                topLeft,
                topRight,
                bottomRight,
                bottomLeft
            )
        )
    }

    private fun resolveClipMode(radii: FloatArray): ClipMode {
        val firstRadius = radii[0]
        val allZero = radii.all { radius -> radius <= 0f }
        if (allZero) return ClipMode.None

        val allEqual = radii.all { radius ->
            RadiusUtils.areEqual(radius, firstRadius)
        }

        return if (allEqual) {
            ClipMode.UniformOutline
        } else {
            ClipMode.Path
        }
    }

    private fun rebuildClipPath(width: Int, height: Int) {
        clipPath.reset()
        if (width <= 0 || height <= 0) return

        clipPath.addRoundRect(
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            cornerRadii,
            Path.Direction.CW
        )
    }

    private class RoundedOutlineProvider(
        private val radius: Float
    ) : ViewOutlineProvider() {

        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, radius)
        }
    }

    private enum class ClipMode {
        None,
        UniformOutline,
        Path
    }
}
