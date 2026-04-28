package gd.app.musicplayer.ui.feature.playlist

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.dpToPx

class PlaylistItemDecoration(
    context: Context,
    @ColorInt dividerColor: Int = context.getColor(R.color.list_divider_color),
    private val dividerHeightPx: Int = 1,
    private val lastRowBottomPaddingPx: Int = context.dpToPx(72f)
) : RecyclerView.ItemDecoration() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = dividerColor
        style = Paint.Style.FILL
    }

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) {
            outRect.setEmpty()
            return
        }

        val isLastRow = isLastRow(parent, position, state.itemCount)
        outRect.set(0, 0, 0, if (isLastRow) lastRowBottomPaddingPx else dividerHeightPx)
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val left = parent.paddingLeft.toFloat()
        val right = (parent.width - parent.paddingRight).toFloat()

        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION || isLastRow(parent, position, state.itemCount)) {
                continue
            }

            val params = child.layoutParams as RecyclerView.LayoutParams
            val top = (child.bottom + params.bottomMargin).toFloat()
            val bottom = top + dividerHeightPx
            canvas.drawRect(left, top, right, bottom, paint)
        }
    }

    private fun isLastRow(parent: RecyclerView, position: Int, itemCount: Int): Boolean {
        if (itemCount <= 0) return false

        val layoutManager = parent.layoutManager
        return if (layoutManager is GridLayoutManager) {
            val spanCount = layoutManager.spanCount
            val spanSizeLookup = layoutManager.spanSizeLookup
            val spanGroup = spanSizeLookup.getSpanGroupIndex(position, spanCount)
            val lastSpanGroup = spanSizeLookup.getSpanGroupIndex(itemCount - 1, spanCount)
            spanGroup == lastSpanGroup
        } else {
            position == itemCount - 1
        }
    }
}
