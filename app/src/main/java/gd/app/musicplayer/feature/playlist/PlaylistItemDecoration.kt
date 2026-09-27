package gd.app.musicplayer.feature.playlist

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.core.common.extension.dpToPx

/**
 * Bottom inset for the last playlist row so content clears the mini player.
 * Row dividers live in the list-item layout (COUI inset divider).
 */
class PlaylistItemDecoration(
    context: Context,
    private val lastRowBottomPaddingPx: Int = context.dpToPx(72f)
) : RecyclerView.ItemDecoration() {

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

        outRect.set(
            0,
            0,
            0,
            if (isLastRow(parent, position, state.itemCount)) lastRowBottomPaddingPx else 0
        )
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
