package gd.app.musicplayer.ui.feature.selection

import android.graphics.Canvas
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager

class DragSwipeCallback(
    private val dragEligibilityChecker: DragEligibilityChecker?
) : ItemTouchHelper.Callback() {

    interface DragEligibilityChecker {
        fun canDrag(position: Int): Boolean
    }

    private var longPressDragEnabled: Boolean = true
    private var dragDirections: Int = 0
    private var swipeDirections: Int = 0

    fun setDragDirections(directions: Int) {
        dragDirections = directions
    }

    fun setLongPressDragEnabled(enabled: Boolean) {
        longPressDragEnabled = enabled
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        if (actionState != ItemTouchHelper.ACTION_STATE_IDLE && viewHolder is ItemTouchStateListener) {
            viewHolder.onItemSelected()
        }
        super.onSelectedChanged(viewHolder, actionState)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        if (viewHolder is ItemTouchStateListener) {
            viewHolder.onItemCleared()
        }
        super.clearView(recyclerView, viewHolder)
    }

    override fun getAnimationDuration(
        recyclerView: RecyclerView,
        animationType: Int,
        animateDx: Float,
        animateDy: Float
    ): Long {
        return if (animationType == ItemTouchHelper.ANIMATION_TYPE_DRAG) {
            300L
        } else {
            super.getAnimationDuration(recyclerView, animationType, animateDx, animateDy)
        }
    }

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val dragFlags = if (
            dragEligibilityChecker == null ||
            dragEligibilityChecker.canDrag(viewHolder.bindingAdapterPosition)
        ) {
            if (dragDirections != 0) {
                dragDirections
            } else {
                when (recyclerView.layoutManager) {
                    is GridLayoutManager,
                    is StaggeredGridLayoutManager -> {
                        ItemTouchHelper.UP or
                                ItemTouchHelper.DOWN or
                                ItemTouchHelper.LEFT or
                                ItemTouchHelper.RIGHT
                    }
                    else -> {
                        ItemTouchHelper.UP or ItemTouchHelper.DOWN
                    }
                }
            }
        } else {
            0
        }

        return makeMovementFlags(dragFlags, swipeDirections)
    }

    override fun isItemViewSwipeEnabled(): Boolean {
        return swipeDirections != 0
    }

    override fun isLongPressDragEnabled(): Boolean {
        return longPressDragEnabled
    }

    override fun onChildDraw(
        canvas: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        source: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        if (source.itemViewType != target.itemViewType) {
            return false
        }

        val adapter = recyclerView.adapter
        if (adapter is ItemMoveListener) {
            adapter.onItemMove(
                source.bindingAdapterPosition,
                target.bindingAdapterPosition
            )
        }
        return true
    }

    override fun onMoved(
        recyclerView: RecyclerView,
        source: RecyclerView.ViewHolder,
        fromPos: Int,
        target: RecyclerView.ViewHolder,
        toPos: Int,
        x: Int,
        y: Int
    ) {
        super.onMoved(recyclerView, source, fromPos, target, toPos, x, y)
        recyclerView.adapter?.notifyItemMoved(
            source.bindingAdapterPosition,
            target.bindingAdapterPosition
        )
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
    }
}

interface ItemTouchStateListener {
    fun onItemSelected()
    fun onItemCleared()
}

interface ItemMoveListener {
    fun onItemMove(fromPosition: Int, toPosition: Int)
}