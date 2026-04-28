package gd.app.musicplayer.ui.common.menu

import android.content.Context
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.PopupWindow
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.appDependencies
import gd.app.musicplayer.core.extension.density
import gd.app.musicplayer.core.extension.dpToPx
import gd.app.musicplayer.data.model.ContextMenuItem
import kotlin.math.max

abstract class BaseContextMenu(
    protected val context: Context
) {

    private var popupWindow: PopupWindow? = null

    private var lastAnchor: View? = null
    private var lastXOff: Int = 0
    private var lastYOff: Int = 0

    protected abstract fun buildItems(): List<ContextMenuItem>

    protected abstract fun onItemClicked(
        item: ContextMenuItem,
        anchor: View
    )

    protected open fun popupWidth(items: List<ContextMenuItem>): Int {
        return calculateMenuWidth(
            context = context,
            items = items
        )
    }

    protected open fun popupHeight(): Int {
        return ViewGroup.LayoutParams.WRAP_CONTENT
    }

    fun show(
        anchor: View,
        xOff: Int = 0,
        yOff: Int = 0
    ) {
        val items = buildItems()

        if (items.isEmpty()) {
            return
        }

        dismiss()

        lastAnchor = anchor
        lastXOff = xOff
        lastYOff = yOff

        val contentView = LayoutInflater
            .from(context)
            .inflate(
                R.layout.b_popupwindow_list,
                null,
                false
            )

        val listView = contentView.findViewById<ListView>(R.id.listView)

        listView.adapter = ContextMenuAdapter(
            context = context,
            items = items
        )

        listView.setOnItemClickListener { _, _, position, _ ->
            val item = items.getOrNull(position) ?: return@setOnItemClickListener

            if (!item.enabled) {
                return@setOnItemClickListener
            }

            onItemClicked(
                item = item,
                anchor = anchor
            )
        }

        popupWindow = PopupWindow(
            contentView,
            popupWidth(items),
            popupHeight(),
            true
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(createBackgroundDrawable())
            elevation = POPUP_ELEVATION_DP * context.density
        }

        popupWindow?.showAsDropDown(
            anchor,
            xOff,
            yOff
        )
    }

    protected fun showAtLastPosition(menu: BaseContextMenu) {
        val anchor = lastAnchor ?: return

        menu.show(
            anchor = anchor,
            xOff = lastXOff,
            yOff = lastYOff
        )
    }

    fun dismiss() {
        popupWindow?.dismiss()
        popupWindow = null
    }

    private fun createBackgroundDrawable(): Drawable {
        return context.appDependencies.themeRepo
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
                MENU_TEXT_SIZE_SP,
                context.resources.displayMetrics
            )
        }

        var maxTextWidth = 0f
        var hasLeftIcon = false
        var hasRightSideContent = false

        items.forEach { item ->
            maxTextWidth = max(
                maxTextWidth,
                paint.measureText(context.getString(item.titleRes))
            )

            if (item.leftIconRes != null) {
                hasLeftIcon = true
            }

            if (item.rightIconRes != null || item.showArrow) {
                hasRightSideContent = true
            }
        }

        var width = maxTextWidth + context.dpToPx(BASE_HORIZONTAL_PADDING_DP)

        if (hasLeftIcon) {
            width += context.dpToPx(LEFT_ICON_EXTRA_WIDTH_DP)
        }

        if (hasRightSideContent) {
            width += context.dpToPx(RIGHT_SIDE_EXTRA_WIDTH_DP)
        }

        return max(
            context.dpToPx(MIN_MENU_WIDTH_DP),
            width.toInt()
        )
    }

    private companion object {
        private const val MENU_TEXT_SIZE_SP = 16f

        private const val MIN_MENU_WIDTH_DP = 168f
        private const val BASE_HORIZONTAL_PADDING_DP = 64f
        private const val LEFT_ICON_EXTRA_WIDTH_DP = 40f
        private const val RIGHT_SIDE_EXTRA_WIDTH_DP = 32f

        private const val POPUP_ELEVATION_DP = 8f
    }
}