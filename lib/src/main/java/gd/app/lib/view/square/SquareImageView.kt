package gd.app.lib.view.square

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import gd.app.lib.R

class SquareImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private var squareConfig: SquareConfig = SquareConfig.default()

    init {
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.SquareFrameLayout)
            val squareMode = typedArray.getInt(R.styleable.SquareFrameLayout_squareMode, 0)
            val squareRatio = SquareConfig.parseRatio(
                typedArray.getString(R.styleable.SquareFrameLayout_squareRatio)
            )
            squareConfig = SquareConfig.create(squareMode, squareRatio)
            typedArray.recycle()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            View.getDefaultSize(0, widthMeasureSpec),
            View.getDefaultSize(0, heightMeasureSpec)
        )
        val measureSpecs = squareConfig.calculateMeasureSpecs(measuredWidth, measuredHeight)
        super.onMeasure(measureSpecs[0], measureSpecs[1])
    }

    fun setSquare(config: SquareConfig) {
        squareConfig = config
        requestLayout()
    }
}
