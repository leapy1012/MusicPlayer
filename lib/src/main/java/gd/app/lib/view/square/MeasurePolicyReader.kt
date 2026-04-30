package gd.app.lib.view.square

import android.content.Context
import android.util.AttributeSet
import gd.app.lib.R

object MeasurePolicyReader {

    fun readForSquareRoundedFrameLayout(
        context: Context,
        attrs: AttributeSet?
    ): MeasureSpecPolicy {
        if (attrs == null) return MeasurePolicyFactory.default()

        val typedArray = context.obtainStyledAttributes(
            attrs,
            R.styleable.SquareRoundedFrameLayout
        )

        return try {
            val mode = typedArray.getInt(
                R.styleable.SquareRoundedFrameLayout_squareFactory,
                MeasurePolicyFactory.MODE_HEIGHT_FROM_WIDTH
            )

            val ratio = MeasurePolicyFactory.parseRatio(
                typedArray.getString(R.styleable.SquareRoundedFrameLayout_squareRatio)
            )

            MeasurePolicyFactory.create(mode, ratio)
        } finally {
            typedArray.recycle()
        }
    }

    fun readForAspectRatioFrameLayout(
        context: Context,
        attrs: AttributeSet?
    ): MeasureSpecPolicy {
        if (attrs == null) return MeasurePolicyFactory.default()

        val typedArray = context.obtainStyledAttributes(
            attrs,
            R.styleable.AspectRatioFrameLayout
        )

        return try {
            val mode = typedArray.getInt(
                R.styleable.AspectRatioFrameLayout_squareFactory,
                MeasurePolicyFactory.MODE_HEIGHT_FROM_WIDTH
            )

            val ratio = MeasurePolicyFactory.parseRatio(
                typedArray.getString(R.styleable.AspectRatioFrameLayout_squareRatio)
            )

            MeasurePolicyFactory.create(mode, ratio)
        } finally {
            typedArray.recycle()
        }
    }

    fun readForAspectRatioImageView(
        context: Context,
        attrs: AttributeSet?
    ): MeasureSpecPolicy {
        if (attrs == null) return MeasurePolicyFactory.default()

        val typedArray = context.obtainStyledAttributes(
            attrs,
            R.styleable.AspectRatioImageView
        )

        return try {
            val mode = typedArray.getInt(
                R.styleable.AspectRatioImageView_squareFactory,
                MeasurePolicyFactory.MODE_HEIGHT_FROM_WIDTH
            )

            val ratio = MeasurePolicyFactory.parseRatio(
                typedArray.getString(R.styleable.AspectRatioImageView_squareRatio)
            )

            MeasurePolicyFactory.create(mode, ratio)
        } finally {
            typedArray.recycle()
        }
    }

    fun readForAspectRatioRelativeLayout(
        context: Context,
        attrs: AttributeSet?
    ): MeasureSpecPolicy {
        if (attrs == null) return MeasurePolicyFactory.default()

        val typedArray = context.obtainStyledAttributes(
            attrs,
            R.styleable.AspectRatioRelativeLayout
        )

        return try {
            val mode = typedArray.getInt(
                R.styleable.AspectRatioRelativeLayout_squareFactory,
                MeasurePolicyFactory.MODE_HEIGHT_FROM_WIDTH
            )

            val ratio = MeasurePolicyFactory.parseRatio(
                typedArray.getString(R.styleable.AspectRatioRelativeLayout_squareRatio)
            )

            MeasurePolicyFactory.create(mode, ratio)
        } finally {
            typedArray.recycle()
        }
    }
}
