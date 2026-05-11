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
    val hasSelectableItems: Boolean,
    val allSelectableItemsSelected: Boolean
)

internal fun Toolbar.installSelectAllAction(
    inflater: LayoutInflater,
    onClick: (ImageView) -> Unit
): ImageView {
    val selectAllButton = inflater.inflate(R.layout.layout_select_all, this, false)
    val selectAllImage = selectAllButton.findViewById<ImageView>(R.id.main_info_selectall)
    selectAllImage.setOnClickListener { onClick(selectAllImage) }
    val layoutParams = Toolbar.LayoutParams(
        Toolbar.LayoutParams.WRAP_CONTENT,
        Toolbar.LayoutParams.MATCH_PARENT
    ).apply {
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
    }
    addView(selectAllButton, layoutParams)
    return selectAllImage
}

internal fun Context.musicSelectionTitle(
    selectedCount: Int,
    emptyTitleRes: Int? = null
): String {
    if (selectedCount <= 0 && emptyTitleRes != null) {
        return getString(emptyTitleRes)
    }
    return if (selectedCount == 1) {
        getString(R.string.select_music, selectedCount)
    } else {
        getString(R.string.select_musics, selectedCount)
    }
}

internal fun ImageView.renderSelectAllState(state: SelectionUiState) {
    isSelected = state.hasSelectableItems && state.allSelectableItemsSelected
    alpha = if (state.hasSelectableItems) 1f else 0.4f
}

internal fun View.renderSelectionVisibility(selectedCount: Int) {
    visibility = if (selectedCount > 0) View.VISIBLE else View.GONE
}

internal fun ViewGroup.updateBulkActionEnabled(enabled: Boolean) {
    children.forEach { child ->
        child.isEnabled = enabled
        child.alpha = if (enabled) 1f else 0.4f
    }
}

internal fun ViewGroup.bindBulkActionClicks(onClick: (View) -> Unit) {
    children.forEach { child ->
        child.setOnClickListener(onClick)
    }
}
