package gd.app.musicplayer.ui.selection

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.children
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
}

internal fun Toolbar.installSelectAllAction(
    inflater: LayoutInflater,
    onClick: () -> Unit
): ImageView {
    val actionView = inflater.inflate(R.layout.layout_select_all, this, false)
    val imageView = actionView.findViewById<ImageView>(R.id.main_info_selectall)

    imageView.setOnClickListener {
        onClick()
    }

    addView(
        actionView,
        Toolbar.LayoutParams(
            Toolbar.LayoutParams.WRAP_CONTENT,
            Toolbar.LayoutParams.MATCH_PARENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
    )

    return imageView
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

internal fun ImageView.renderSelectAllState(state: SelectionUiState) {
    isSelected = state.allSelected
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