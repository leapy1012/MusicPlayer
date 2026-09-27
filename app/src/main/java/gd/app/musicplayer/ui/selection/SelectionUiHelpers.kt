package gd.app.musicplayer.ui.selection

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.view.children
import com.coui.appcompat.checkbox.COUICheckBox
import gd.app.musicplayer.R

internal data class SelectionUiState(
    val selectedCount: Int,
    val selectableCount: Int
) {
    val hasSelection: Boolean
        get() = selectedCount > 0

    val hasSelectableItems: Boolean
        get() = selectableCount > 0

    val allSelected: Boolean
        get() = hasSelectableItems && selectedCount == selectableCount

    val partiallySelected: Boolean
        get() = hasSelection && !allSelected
}

/**
 * Replaces the toolbar menu with the COUI select-all action. The menu inflates after the
 * screen's theme pass, so callers re-run the theme engine on the toolbar for palette themes.
 */
internal fun Toolbar.installSelectAllMenu(onClick: () -> Unit) {
    menu.clear()
    inflateMenu(R.menu.menu_fragment_select)
    setOnMenuItemClickListener { item ->
        if (item.itemId == R.id.menu_select_all) {
            onClick()
            true
        } else {
            false
        }
    }
}

internal fun Toolbar.renderSelectAllMenu(state: SelectionUiState) {
    val item = menu.findItem(R.id.menu_select_all) ?: return
    item.isEnabled = state.hasSelectableItems
    item.icon?.alpha = when {
        !state.hasSelectableItems -> SELECT_ALL_ICON_ALPHA_DISABLED
        state.allSelected -> SELECT_ALL_ICON_ALPHA_ALL
        else -> SELECT_ALL_ICON_ALPHA_PARTIAL
    }
}

internal fun Context.musicSelectionTitle(
    selectedCount: Int,
    emptyTitleRes: Int
): String {
    return when {
        selectedCount <= 0 -> getString(emptyTitleRes)
        selectedCount == 1 -> getString(R.string.item_selected, selectedCount)
        else -> getString(R.string.items_selected, selectedCount)
    }
}

internal fun COUICheckBox.renderSelectAllState(state: SelectionUiState) {
    setState(
        when {
            state.allSelected -> COUICheckBox.SELECT_ALL
            state.partiallySelected -> COUICheckBox.SELECT_PART
            else -> COUICheckBox.SELECT_NONE
        }
    )
    isEnabled = state.hasSelectableItems
    alpha = if (state.hasSelectableItems) ENABLED_ALPHA else DISABLED_ALPHA
}

internal fun ViewGroup.updateBulkActionEnabled(enabled: Boolean) {
    children.forEach { child ->
        child.isEnabled = enabled
        child.alpha = if (enabled) ENABLED_ALPHA else DISABLED_ALPHA
    }
}

internal fun ViewGroup.bindBulkActionClicks(onClick: (View) -> Unit) {
    children.forEach { child ->
        child.setOnClickListener(onClick)
    }
}

private const val ENABLED_ALPHA = 1f
private const val DISABLED_ALPHA = 0.4f

private const val SELECT_ALL_ICON_ALPHA_DISABLED = 102
private const val SELECT_ALL_ICON_ALPHA_PARTIAL = 180
private const val SELECT_ALL_ICON_ALPHA_ALL = 255
