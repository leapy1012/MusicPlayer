package gd.app.musicplayer.ui.common

import com.coui.appcompat.searchview.COUISearchBar

/**
 * In STATE_NORMAL [COUISearchBar] intercepts every touch and never enters edit mode by itself,
 * so the host must switch states on click or the field can never be focused.
 */
fun COUISearchBar.enableTapToEdit() {
    setOnClickListener {
        if (searchState == COUISearchBar.STATE_NORMAL) {
            changeStateWithAnimation(COUISearchBar.STATE_EDIT)
        }
    }
}

fun COUISearchBar.exitEditMode() {
    if (searchState == COUISearchBar.STATE_EDIT) {
        changeStateImmediately(COUISearchBar.STATE_NORMAL)
    }
}

/**
 * In-place filter search under a fixed toolbar.
 * [COUISearchBar.TYPE_INSTANT_SEARCH] slides the bar up via topMargin and breaks that layout.
 */
fun COUISearchBar.enableInPlaceFilterSearch(onCancel: (() -> Unit)? = null) {
    setSearchAnimateType(COUISearchBar.TYPE_NON_INSTANT_SEARCH)
    enableTapToEdit()
    functionalButton?.setOnClickListener {
        onCancel?.invoke()
        exitEditMode()
    }
}
