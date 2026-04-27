package gd.app.musicplayer.ui.common.base

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class SpacingItemDecoration private constructor(
    private val left: Int,
    private val top: Int,
    private val right: Int,
    private val bottom: Int
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        outRect.set(left, top, right, bottom)
    }

    companion object {
        fun all(spacing: Int): SpacingItemDecoration =
            SpacingItemDecoration(
                left = spacing,
                top = spacing,
                right = spacing,
                bottom = spacing
            )

        fun symmetric(horizontal: Int, vertical: Int): SpacingItemDecoration =
            SpacingItemDecoration(
                left = horizontal,
                top = vertical,
                right = horizontal,
                bottom = vertical
            )

        fun right(spacing: Int): SpacingItemDecoration =
            SpacingItemDecoration(
                left = 0,
                top = 0,
                right = spacing,
                bottom = 0
            )
    }
}
