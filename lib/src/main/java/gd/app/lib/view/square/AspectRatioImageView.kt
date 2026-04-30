package gd.app.lib.view.square

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatImageView

class AspectRatioImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private var measurePolicy: MeasureSpecPolicy =
        MeasurePolicyReader.readForAspectRatioImageView(context, attrs)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            View.getDefaultSize(0, widthMeasureSpec),
            View.getDefaultSize(0, heightMeasureSpec)
        )

        val specs = measurePolicy.createMeasureSpecs(
            measuredWidth = measuredWidth,
            measuredHeight = measuredHeight
        )

        super.onMeasure(specs[0], specs[1])
    }

    fun setMeasurePolicy(policy: MeasureSpecPolicy) {
        measurePolicy = policy
        requestLayout()
    }

    fun setSquare(policy: MeasureSpecPolicy) {
        setMeasurePolicy(policy)
    }
}
