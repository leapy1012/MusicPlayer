package gd.app.musicplayer.core.ui.view

import android.content.Context
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.PopupWindow
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.drawable.DrawableCompat
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.dpToPx
import kotlin.math.max
import dagger.hilt.android.EntryPointAccessors
import gd.app.musicplayer.di.ThemeEntryPoint

class CustomSpinner @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs), View.OnClickListener {

    private var popupWindow: PopupWindow? = null
    private var itemClickListener: AdapterView.OnItemClickListener? = null
    private var entries: Array<String>? = null
    private var selectedIndex: Int = -1
    private var arrowDrawable: Drawable? = null

    init {
        setOnClickListener(this)
        AppCompatResources.getDrawable(context, R.drawable.vector_arrow_down)?.let { arrow ->
            arrowDrawable = DrawableCompat.wrap(arrow).mutate()
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
        popupWindow?.dismiss()
        popupWindow = null
    }

    fun getSelection(): Int = selectedIndex

    fun setCustomText(value: String?) {
        selectedIndex = -1
        text = value ?: ""
    }

    fun setEntries(values: Array<String>?) {
        entries = values
        updateDisplayedText()
        popupWindow?.dismiss()
        popupWindow = null
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
        popupWindow?.dismiss()

        val listView = ListView(context).apply {
            divider = null
            adapter = ArrayAdapter(
                context,
                android.R.layout.simple_list_item_1,
                items
            )
            setOnItemClickListener { _, itemView, position, id ->
                popupWindow?.dismiss()
                if (selectedIndex != position) {
                    selectedIndex = position
                    updateDisplayedText()
                    itemClickListener?.onItemClick(this, itemView, position, id)
                }
            }
        }

        val popupWidth = calculatePopupWidth(items)
        val popupHeight = calculatePopupHeight(items)

        popupWindow = PopupWindow(
            listView,
            popupWidth,
            popupHeight,
            true
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(BasePopupBackgroundProvider.background(context))
            elevation = 8f * context.resources.displayMetrics.density
        }

        popupWindow?.showAsDropDown(anchor)
    }

    private fun calculatePopupWidth(items: Array<String>): Int {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = textSize
        }
        var maxTextWidth = 0f
        for (item in items) {
            maxTextWidth = max(maxTextWidth, paint.measureText(item))
        }
        val desired = (maxTextWidth + context.dpToPx(32f)).toInt()
        val minWidth = context.dpToPx(168f)
        val maxWidth = context.dpToPx(240f)
        return desired.coerceIn(minWidth, maxWidth)
    }

    private fun calculatePopupHeight(items: Array<String>): Int {
        val rowHeight = context.dpToPx(40f)
        val maxHeight = context.dpToPx(360f)
        val desired = rowHeight * items.size
        return if (desired > maxHeight) maxHeight else ViewGroup.LayoutParams.WRAP_CONTENT
    }

    private object BasePopupBackgroundProvider {

        fun background(context: Context): Drawable {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                ThemeEntryPoint::class.java
            )

            return entryPoint.themeRepo
                .getCorePalette()
                .getPopupBackgroundDrawable(context)
        }
    }
}
