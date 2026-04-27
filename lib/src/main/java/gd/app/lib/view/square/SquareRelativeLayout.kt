package gd.app.lib.view.square

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import gd.app.lib.R
import androidx.core.content.withStyledAttributes

class SquareRelativeLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {

    private var squareConfig: SquareConfig = SquareConfig.default()

    init {
        attrs?.let {
            context.withStyledAttributes(
                it,
                R.styleable.SquareRelativeLayout
            ) {

                val mode = getInt(
                    R.styleable.SquareRelativeLayout_squareMode,
                    0
                )

                val ratio = getString(
                    R.styleable.SquareRelativeLayout_squareRatio
                )

                squareConfig = SquareConfig.create(mode, SquareConfig.parseRatio(ratio))

            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = getDefaultSize(0, widthMeasureSpec)
        val height = getDefaultSize(0, heightMeasureSpec)

        setMeasuredDimension(width, height)

        val newMeasure = squareConfig.calculateMeasureSpecs(width, height)

        super.onMeasure(newMeasure[0], newMeasure[1])
    }

    fun setSquare(sizePx: Int) {
        squareConfig = SquareConfig.fixed(sizePx)
        requestLayout()
    }
}
