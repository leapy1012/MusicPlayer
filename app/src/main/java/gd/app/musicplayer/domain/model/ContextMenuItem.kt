package gd.app.musicplayer.domain.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * UI-ready menu item model used by bottom sheets/dialog menus.
 */
data class ContextMenuItem(
    val id: String,
    @param:StringRes val titleRes: Int,
    @param:DrawableRes val leftIconRes: Int? = null,
    @param:DrawableRes val rightIconRes: Int? = null,
    val showArrow: Boolean = false,
    val enabled: Boolean = true,
    val selected: Boolean = false
)