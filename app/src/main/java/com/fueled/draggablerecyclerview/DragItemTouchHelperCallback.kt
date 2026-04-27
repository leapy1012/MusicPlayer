package com.fueled.draggablerecyclerview

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class DragItemTouchHelperCallback private constructor(
    dragDirs: Int,
    swipeDirs: Int
) : ItemTouchHelper.SimpleCallback(dragDirs, swipeDirs) {

    fun interface DragEligibilityChecker {
        fun canDrag(position: Int): Boolean
    }

    private var dragEnabled: Boolean = false
    private var onItemDragListener: OnItemDragListener? = null
    private var dragEligibilityChecker: DragEligibilityChecker? = null

    private constructor(builder: Builder) : this(builder.dragDirs, builder.swipeDirs) {
        dragEnabled = builder.dragEnabled
        onItemDragListener = builder.onItemDragListener
        dragEligibilityChecker = builder.dragEligibilityChecker
    }

    override fun isLongPressDragEnabled(): Boolean = dragEnabled

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val position = viewHolder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) {
            return 0
        }

        if (dragEligibilityChecker?.canDrag(position) == false) {
            return makeMovementFlags(0, 0)
        }

        return super.getMovementFlags(recyclerView, viewHolder)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        source: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        if (source.itemViewType != target.itemViewType) {
            return false
        }

        onItemDragListener?.onItemDragged(
            source.bindingAdapterPosition,
            target.bindingAdapterPosition
        )
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
            viewHolder?.itemView?.alpha = DRAG_ALPHA
        }
        super.onSelectedChanged(viewHolder, actionState)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        viewHolder.itemView.alpha = ALPHA_FULL
        super.clearView(recyclerView, viewHolder)
    }

    fun interface OnItemDragListener {
        fun onItemDragged(indexFrom: Int, indexTo: Int)
    }

    class Builder(
        internal val dragDirs: Int,
        internal val swipeDirs: Int
    ) {
        internal var onItemDragListener: OnItemDragListener? = null
        internal var dragEnabled: Boolean = false
        internal var dragEligibilityChecker: DragEligibilityChecker? = null

        fun onItemDragListener(value: OnItemDragListener): Builder {
            onItemDragListener = value
            return this
        }

        fun setDragEnabled(value: Boolean): Builder {
            dragEnabled = value
            return this
        }

        fun dragEligibilityChecker(value: DragEligibilityChecker): Builder {
            dragEligibilityChecker = value
            return this
        }

        fun build(): DragItemTouchHelperCallback = DragItemTouchHelperCallback(this)
    }

    companion object {
        const val ALPHA_FULL = 1.0f
        private const val DRAG_ALPHA = 0.8f
    }
}
