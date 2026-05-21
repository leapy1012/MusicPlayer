package gd.app.musicplayer.domain.model

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * Legacy-compatible menu model.
 *
 * New code should prefer immutable construction through [copy], while existing Java/Kotlin call
 * sites can still use the factory and fluent setter methods below.
 */
data class MenuItemModel(
    @param:StringRes private val titleResId: Int = 0,
    private val customTitle: String? = null,
    private val accentStyle: Boolean = false,
    private val destructiveStyle: Boolean = false,
    private val toggleStyle: Boolean = false,
    @param:DrawableRes private val iconResId: Int = 0,
    private val backgroundStyleResId: Int = 0
) {

    fun getIconResId(): Int = iconResId

    fun getBackgroundStyleResId(): Int = backgroundStyleResId

    fun getTitle(context: Context): String {
        return customTitle ?: context.getString(titleResId)
    }

    fun getTitleResId(): Int = titleResId

    fun hasIcon(): Boolean {
        return iconResId != 0
    }

    fun hasBackgroundStyle(): Boolean {
        return backgroundStyleResId != 0
    }

    fun isToggleStyle(): Boolean = toggleStyle

    fun isAccentStyle(): Boolean = accentStyle

    fun isDestructiveStyle(): Boolean = destructiveStyle

    fun setCustomTitle(title: String): MenuItemModel {
        return copy(customTitle = title)
    }

    fun setIcon(@DrawableRes iconResId: Int): MenuItemModel {
        return copy(iconResId = iconResId)
    }

    companion object {

        fun create(@StringRes titleResId: Int): MenuItemModel {
            return MenuItemModel(titleResId = titleResId)
        }

        fun createToggleItem(
            @StringRes titleResId: Int,
            isToggleStyle: Boolean
        ): MenuItemModel {
            return MenuItemModel(
                titleResId = titleResId,
                toggleStyle = isToggleStyle
            )
        }

        fun createAccentItem(@StringRes titleResId: Int): MenuItemModel {
            return MenuItemModel(
                titleResId = titleResId,
                accentStyle = true
            )
        }

        fun createDestructiveItem(@StringRes titleResId: Int): MenuItemModel {
            return MenuItemModel(
                titleResId = titleResId,
                destructiveStyle = true
            )
        }
    }
}