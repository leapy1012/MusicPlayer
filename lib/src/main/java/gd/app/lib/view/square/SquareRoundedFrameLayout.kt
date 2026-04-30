package gd.app.lib.view.square

import android.content.Context
import android.util.AttributeSet
import android.view.View

class SquareRoundedFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : RoundedCornerFrameLayout(context, attrs) {

    private var measurePolicy: MeasureSpecPolicy? =
        MeasurePolicyReader.readForSquareRoundedFrameLayout(context, attrs)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val policy = measurePolicy

        if (policy == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        setMeasuredDimension(
            View.getDefaultSize(0, widthMeasureSpec),
            View.getDefaultSize(0, heightMeasureSpec)
        )

        val specs = policy.createMeasureSpecs(
            measuredWidth = measuredWidth,
            measuredHeight = measuredHeight
        )

        forceLayout()
        super.onMeasure(specs[0], specs[1])
    }

    fun setMeasurePolicy(policy: MeasureSpecPolicy?) {
        measurePolicy = policy
        requestLayout()
    }

    fun setSquare(policy: MeasureSpecPolicy?) {
        setMeasurePolicy(policy)
    }
}
