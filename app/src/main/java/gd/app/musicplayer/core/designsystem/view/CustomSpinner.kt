package gd.app.musicplayer.core.designsystem.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.AdapterView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.drawable.DrawableCompat
import com.coui.appcompat.poplist.COUIPopupListWindow
import com.coui.appcompat.poplist.PopupListItem
import gd.app.musicplayer.R
import gd.app.musicplayer.ui.common.menu.CouiPopupListSurface

class CustomSpinner @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs), View.OnClickListener {

    private var popupWindow: COUIPopupListWindow? = null
    private var itemClickListener: AdapterView.OnItemClickListener? = null
    private var entries: Array<String>? = null
    private var selectedIndex: Int = -1
    private var arrowDrawable: Drawable? = null

    init {
        setOnClickListener(this)
        AppCompatResources.getDrawable(context, R.drawable.vector_arrow_down)?.let { arrow ->
            arrowDrawable = DrawableCompat.wrap(arrow).mutate().also {
                DrawableCompat.setTintList(it, textColors)
            }
            setCompoundDrawablesWithIntrinsicBounds(null, null, arrowDrawable, null)
        }
    }

    override fun onClick(anchor: View) {
        val items = entries ?: return
        if (items.isEmpty()) return
        showPopup(anchor, items)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        dismissPopup()
    }

    fun getSelection(): Int = selectedIndex

    fun setCustomText(value: String?) {
        selectedIndex = -1
        text = value ?: ""
    }

    fun setEntries(values: Array<String>?) {
        entries = values
        updateDisplayedText()
        dismissPopup()
    }

    fun setEntriesResourceId(arrayResId: Int) {
        setEntries(resources.getStringArray(arrayResId))
    }

    fun setOnItemClickListener(listener: AdapterView.OnItemClickListener?) {
        itemClickListener = listener
    }

    fun setSelection(index: Int) {
        selectedIndex = index
        updateDisplayedText()
    }

    override fun setTextColor(color: Int) {
        super.setTextColor(color)
        arrowDrawable?.let { drawable ->
            DrawableCompat.setTint(drawable, color)
            setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null)
        }
    }

    private fun updateDisplayedText() {
        val currentEntries = entries
        val index = selectedIndex
        if (currentEntries == null || index < 0 || index >= currentEntries.size) {
            if (index >= 0) {
                text = ""
            }
            return
        }
        text = currentEntries[index]
    }

    private fun showPopup(anchor: View, items: Array<String>) {
        dismissPopup()

        val popupItems = ArrayList(
            items.mapIndexed { index, title ->
                PopupListItem.Builder()
                    .setId(index)
                    .setTitle(title)
                    .setIsEnable(true)
                    .setIsChecked(index == selectedIndex)
                    .build()
            }
        )

        val window = COUIPopupListWindow(context).also { popupWindow = it }
        window.setItemList(popupItems)
        window.setOnItemClickListener { parent, itemView, position, id ->
            dismissPopup()
            if (selectedIndex != position) {
                selectedIndex = position
                updateDisplayedText()
                itemClickListener?.onItemClick(parent, itemView, position, id)
            }
        }
        window.show(anchor)
        CouiPopupListSurface.apply(window, context)
        anchor.post {
            if (popupWindow === window) CouiPopupListSurface.apply(window, context)
        }
    }

    private fun dismissPopup() {
        popupWindow?.dismiss()
        popupWindow = null
    }
}
