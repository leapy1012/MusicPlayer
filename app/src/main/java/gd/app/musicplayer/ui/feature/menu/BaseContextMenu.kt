package gd.app.musicplayer.ui.feature.menu

import android.content.Context
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.PopupWindow
import android.util.TypedValue
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.ContextMenuItem
import gd.app.musicplayer.core.ui.extension.appContainer
import gd.app.musicplayer.core.ui.extension.density
import gd.app.musicplayer.core.ui.extension.dpToPx
import kotlin.math.max

abstract class BaseContextMenu(
    private val context: Context,
) {
    private var popupWindow: PopupWindow? = null
    private var lastAnchor: View? = null
    private var lastXOff: Int = 0
    private var lastYOff: Int = 0

    protected abstract fun buildItems(): List<ContextMenuItem>
    protected abstract fun onItemClicked(item: ContextMenuItem, anchor: View)

    open fun popupWidth(items: List<ContextMenuItem>): Int = calculateMenuWidth(context, items)
    open fun popupHeight(): Int = ViewGroup.LayoutParams.WRAP_CONTENT

    fun show(anchor: View, xOff: Int = 0, yOff: Int = 0) {
        lastAnchor = anchor
        lastXOff = xOff
        lastYOff = yOff

        val contentView = LayoutInflater.from(context)
            .inflate(R.layout.b_popupwindow_list, null, false)

        val listView = contentView.findViewById<ListView>(R.id.listView)
        val items = buildItems()

        listView.adapter = ContextMenuAdapter(context, items)
        listView.setOnItemClickListener { _, itemView, position, _ ->
            val item = items[position]
            if (!item.enabled) return@setOnItemClickListener
            onItemClicked(item, listView)
        }

        popupWindow = PopupWindow(
            contentView,
            popupWidth(items),
            popupHeight(),
            true
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(getBackground(context))
            elevation = 8f * context.density
        }

        popupWindow?.showAsDropDown(anchor, xOff, yOff)
    }

    protected fun showAtLastPosition(menu: BaseContextMenu) {
        val anchor = lastAnchor ?: return
        menu.show(anchor, lastXOff, lastYOff)
    }

    fun getBackground(context: Context): Drawable {
        return context.appContainer.themeRepo
            .getCorePalette(context)
            .getPopupBackgroundDrawable(context)
    }

    private fun calculateMenuWidth(
        context: Context,
        items: List<ContextMenuItem>
    ): Int {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                16f,
                context.resources.displayMetrics
            )
        }

        var maxTextWidth = 0f
        var hasLeftIcon = false
        var hasRightSide = false

        for (item in items) {
            maxTextWidth = max(maxTextWidth, paint.measureText(context.getString(item.titleRes)))
            if (item.leftIconRes != null) hasLeftIcon = true
            if (item.rightIconRes != null || item.showArrow) hasRightSide = true
        }

        var width = maxTextWidth + context.dpToPx(64f)
        if (hasLeftIcon) width += context.dpToPx(40f)
        if (hasRightSide) width += context.dpToPx(32f)

        return max(context.dpToPx(168f), width.toInt())
    }

    fun dismiss() {
        popupWindow?.dismiss()
        popupWindow = null
    }
}
