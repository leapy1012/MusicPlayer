package gd.app.musicplayer.ui.common.menu

import android.content.Context
import android.view.View
import android.widget.AdapterView
import com.coui.appcompat.poplist.COUIPopupListWindow
import com.coui.appcompat.poplist.PopupListItem
import gd.app.musicplayer.domain.model.MusicSet
import java.util.ArrayList

/**
 * Selection-mode sort menu backed by COUI [COUIPopupListWindow].
 */
class SortByContextMenu(
    private val context: Context,
    private val musicSet: MusicSet,
    private val currentSortStyle: String = "",
    private val currentSortDescending: Boolean = false,
    private val onSortChanged: ((String, Boolean) -> Unit)? = null
) {

    private var popup: COUIPopupListWindow? = null

    fun show(anchor: View) {
        dismiss()

        val options = MusicSetSortOptions.build(
            musicSet = musicSet,
            currentSortStyle = currentSortStyle,
            currentSortDescending = currentSortDescending
        )
        if (options.isEmpty()) return

        val items = ArrayList(
            options.map { option ->
                PopupListItem.Builder()
                    .setId(option.id)
                    .setTitle(context.getString(option.titleRes))
                    .setIsEnable(true)
                    .setIsChecked(option.isSelected)
                    .build()
            }
        )
        CouiPopupListSurface.paintItemTitles(context, items)

        val window = COUIPopupListWindow(
            CouiPopupListSurface.popupContext(context)
        ).also { popup = it }
        window.setItemList(items)
        window.setOnItemClickListener(AdapterView.OnItemClickListener { _, _, position, _ ->
            val option = options.getOrNull(position) ?: return@OnItemClickListener
            onSortChanged?.invoke(option.style, option.reversed)
            dismiss()
        })
        window.show(anchor)
        CouiPopupListSurface.apply(window, context)
        anchor.post {
            if (popup === window) CouiPopupListSurface.apply(window, context)
        }
    }

    fun dismiss() {
        popup?.dismiss()
        popup = null
    }
}
