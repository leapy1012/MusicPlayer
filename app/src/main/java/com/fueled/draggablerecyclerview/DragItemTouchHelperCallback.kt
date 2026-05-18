package com.fueled.draggablerecyclerview

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class DragItemTouchHelperCallback private constructor(
    dragDirs: Int,
    swipeDirs: Int
) : ItemTouchHelper.SimpleCallback(
    dragDirs,
    swipeDirs
) {

    fun interface DragEligibilityChecker {
        fun canDrag(position: Int): Boolean
    }

    fun interface OnItemDragListener {
        fun onItemDragged(
            indexFrom: Int,
            indexTo: Int
        )
    }

    fun interface OnDragFinishedListener {
        fun onDragFinished()
    }

    private var dragEnabled: Boolean = false
    private var onItemDragListener: OnItemDragListener? = null
    private var dragEligibilityChecker: DragEligibilityChecker? = null
    private var onDragFinishedListener: OnDragFinishedListener? = null

    private var hasActiveDrag = false

    private constructor(builder: Builder) : this(
        dragDirs = builder.dragDirs,
        swipeDirs = builder.swipeDirs
    ) {
        dragEnabled = builder.dragEnabled
        onItemDragListener = builder.onItemDragListener
        dragEligibilityChecker = builder.dragEligibilityChecker
        onDragFinishedListener = builder.onDragFinishedListener
    }

    override fun isLongPressDragEnabled(): Boolean {
        return dragEnabled
    }

    override fun isItemViewSwipeEnabled(): Boolean {
        return false
    }

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val position = viewHolder.bindingAdapterPosition

        if (position == RecyclerView.NO_POSITION) {
            return makeMovementFlags(0, 0)
        }

        if (dragEligibilityChecker?.canDrag(position) == false) {
            return makeMovementFlags(0, 0)
        }

        return super.getMovementFlags(
            recyclerView,
            viewHolder
        )
    }

    override fun canDropOver(
        recyclerView: RecyclerView,
        current: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val currentPosition = current.bindingAdapterPosition
        val targetPosition = target.bindingAdapterPosition

        if (currentPosition == RecyclerView.NO_POSITION) return false
        if (targetPosition == RecyclerView.NO_POSITION) return false
        if (current.itemViewType != target.itemViewType) return false

        if (dragEligibilityChecker?.canDrag(currentPosition) == false) return false
        if (dragEligibilityChecker?.canDrag(targetPosition) == false) return false

        return true
    }

    override fun onMove(
        recyclerView: RecyclerView,
        source: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val fromPosition = source.bindingAdapterPosition
        val toPosition = target.bindingAdapterPosition

        if (fromPosition == RecyclerView.NO_POSITION) return false
        if (toPosition == RecyclerView.NO_POSITION) return false
        if (fromPosition == toPosition) return false
        if (source.itemViewType != target.itemViewType) return false

        if (dragEligibilityChecker?.canDrag(fromPosition) == false) return false
        if (dragEligibilityChecker?.canDrag(toPosition) == false) return false

        onItemDragListener?.onItemDragged(
            fromPosition,
            toPosition
        )

        return true
    }

    override fun onSwiped(
        viewHolder: RecyclerView.ViewHolder,
        direction: Int
    ) = Unit

    override fun onSelectedChanged(
        viewHolder: RecyclerView.ViewHolder?,
        actionState: Int
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            hasActiveDrag = true
            viewHolder?.itemView?.alpha = DRAG_ALPHA
        }

        super.onSelectedChanged(
            viewHolder,
            actionState
        )
    }

    override fun clearView(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ) {
        val wasDragging = hasActiveDrag

        viewHolder.itemView.alpha = ALPHA_FULL

        super.clearView(
            recyclerView,
            viewHolder
        )

        if (wasDragging) {
            hasActiveDrag = false
            onDragFinishedListener?.onDragFinished()
        }
    }

    override fun getAnimationDuration(
        recyclerView: RecyclerView,
        animationType: Int,
        animateDx: Float,
        animateDy: Float
    ): Long {
        if (animationType == ItemTouchHelper.ANIMATION_TYPE_DRAG) {
            return DRAG_ANIMATION_DURATION_MS
        }

        return super.getAnimationDuration(
            recyclerView,
            animationType,
            animateDx,
            animateDy
        )
    }

    class Builder(
        internal val dragDirs: Int,
        internal val swipeDirs: Int
    ) {
        internal var dragEnabled: Boolean = false
        internal var onItemDragListener: OnItemDragListener? = null
        internal var dragEligibilityChecker: DragEligibilityChecker? = null
        internal var onDragFinishedListener: OnDragFinishedListener? = null

        fun setDragEnabled(value: Boolean): Builder {
            dragEnabled = value
            return this
        }

        fun onItemDragListener(value: OnItemDragListener): Builder {
            onItemDragListener = value
            return this
        }

        fun dragEligibilityChecker(value: DragEligibilityChecker): Builder {
            dragEligibilityChecker = value
            return this
        }

        fun onDragFinishedListener(value: OnDragFinishedListener): Builder {
            onDragFinishedListener = value
            return this
        }

        fun build(): DragItemTouchHelperCallback {
            return DragItemTouchHelperCallback(this)
        }
    }

    companion object {
        const val ALPHA_FULL = 1.0f

        private const val DRAG_ALPHA = 0.82f
        private const val DRAG_ANIMATION_DURATION_MS = 250L
    }
}