package gd.app.musicplayer.ui.common.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatImageView

class SelectBox(
    context: Context,
    attrs: AttributeSet
) : AppCompatImageView(context, attrs), View.OnClickListener {

    interface OnSelectChangedListener {
        fun onSelectChanged(selectBox: SelectBox, fromUser: Boolean, isSelected: Boolean)
    }

    private var onSelectChangedListener: OnSelectChangedListener? = null

    init {
        setOnClickListener(this)
    }

    fun getOnSelectChangedListener(): OnSelectChangedListener? {
        return onSelectChangedListener
    }

    fun setOnSelectChangedListener(listener: OnSelectChangedListener?) {
        onSelectChangedListener = listener
    }

    override fun onClick(v: View?) {
        isSelected = !isSelected
        onSelectChangedListener?.onSelectChanged(this, true, isSelected)
    }

    override fun setSelected(selected: Boolean) {
        super.setSelected(selected)
        onSelectChangedListener?.onSelectChanged(this, false, isSelected)
    }
}