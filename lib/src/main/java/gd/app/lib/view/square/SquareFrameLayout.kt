package gd.app.lib.view.square

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.withStyledAttributes
import gd.app.lib.R

class SquareFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var squareConfig: SquareConfig = SquareConfig.default()

    init {
        attrs?.let {
            context.withStyledAttributes(
                it,
                R.styleable.SquareFrameLayout
            ) {

                val mode = getInt(
                    R.styleable.SquareFrameLayout_squareMode,
                    0
                )

                val ratio = getString(
                    R.styleable.SquareFrameLayout_squareRatio
                )

                squareConfig = SquareConfig.create(mode, SquareConfig.parseRatio(ratio))

            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {

        val config = squareConfig

        val measuredWidth = getDefaultSize(0, widthMeasureSpec)
        val measuredHeight = getDefaultSize(0, heightMeasureSpec)

        setMeasuredDimension(measuredWidth, measuredHeight)

        val adjustedSpecs = config.calculateMeasureSpecs(
            measuredWidth,
            measuredHeight
        )

        val adjustedWidthSpec = adjustedSpecs[0]
        val adjustedHeightSpec = adjustedSpecs[1]

        super.onMeasure(adjustedWidthSpec, adjustedHeightSpec)
    }

    fun setSquareConfig(config: SquareConfig) {
        squareConfig = config
        requestLayout()
    }
}