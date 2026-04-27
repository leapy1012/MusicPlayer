package gd.app.musicplayer.ui.common.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView


class EqualizerSingleGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), View.OnClickListener {

    interface OnSingleSelectionChangedListener {
        fun onSelectionChanged(parent: ViewGroup, clickedView: View, selectedIndex: Int)
    }

    private val selectableTextViews = ArrayList<View>()
    private var selectedIndex = -1
    private var selectionChangedListener: OnSingleSelectionChangedListener? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        selectableTextViews.clear()
        collectSelectableChildren(this)
    }

    override fun onClick(view: View) {
        if (view.isSelected) {
            view.isSelected = false
            selectedIndex = -1
        } else {
            selectableTextViews.forEachIndexed { index, child ->
                val isSelected = child === view
                child.isSelected = isSelected
                if (isSelected) {
                    selectedIndex = index
                }
            }
        }

        selectionChangedListener?.onSelectionChanged(this, view, selectedIndex)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        selectableTextViews.forEach { it.isEnabled = enabled }
    }

    fun setOnSingleSelectListener(listener: OnSingleSelectionChangedListener?) {
        selectionChangedListener = listener
    }

    fun setSelectedIndex(index: Int) {
        if (selectedIndex == index) return

        selectedIndex = index
        selectableTextViews.forEachIndexed { currentIndex, child ->
            child.isSelected = currentIndex == index
        }
    }

    fun getSelectedIndex(): Int = selectedIndex

    private fun collectSelectableChildren(parent: ViewGroup) {
        for (childIndex in 0 until parent.childCount) {
            when (val child = parent.getChildAt(childIndex)) {
                is ViewGroup -> collectSelectableChildren(child)
                is TextView -> {
                    child.setOnClickListener(this)
                    selectableTextViews += child
                }
            }
        }
    }
}
