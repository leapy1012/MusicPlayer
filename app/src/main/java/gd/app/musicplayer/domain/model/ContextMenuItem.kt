package gd.app.musicplayer.domain.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

data class ContextMenuItem(
    val id: String,
    @param:StringRes @field:StringRes val titleRes: Int,
    @param:DrawableRes @field:DrawableRes val leftIconRes: Int? = null,
    @param:DrawableRes @field:DrawableRes val rightIconRes: Int? = null,
    val showArrow: Boolean = false,
    val enabled: Boolean = true,
    val selected: Boolean = false
)
