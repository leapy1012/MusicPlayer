package gd.app.musicplayer.domain.model

//package: l4.d
import android.content.Context

class MenuItemModel {

    private var titleResId: Int = 0
    private var customTitle: String? = null

    private var isAccentStyle: Boolean = false
    private var isDestructiveStyle: Boolean = false
    private var isToggleStyle: Boolean = false

    private var iconResId: Int = 0
    private var backgroundStyleResId: Int = 0

    companion object {

        // original: a
        fun create(titleResId: Int): MenuItemModel {
            return MenuItemModel().apply {
                this.titleResId = titleResId
            }
        }

        // original: b
        fun createToggleItem(titleResId: Int, isToggleStyle: Boolean): MenuItemModel {
            return MenuItemModel().apply {
                this.titleResId = titleResId
                this.isToggleStyle = isToggleStyle
//                this.backgroundStyleResId = i4.a.f10014b
            }
        }

        // original: c
        fun createAccentItem(titleResId: Int): MenuItemModel {
            return MenuItemModel().apply {
                this.titleResId = titleResId
                this.isAccentStyle = true
//                this.backgroundStyleResId = i4.a.f10013a
            }
        }

        // original: d
        fun createDestructiveItem(titleResId: Int): MenuItemModel {
            return MenuItemModel().apply {
                this.titleResId = titleResId
                this.isDestructiveStyle = true
            }
        }
    }

    // original: e
    fun getIconResId(): Int = iconResId

    // original: f
    fun getBackgroundStyleResId(): Int = backgroundStyleResId

    // original: g
    fun getTitle(context: Context): String {
        return customTitle ?: context.getString(titleResId)
    }

    // original: h
    fun getTitleResId(): Int = titleResId

    // original: i
    fun hasIcon(): Boolean = iconResId != 0

    // original: j
    fun hasBackgroundStyle(): Boolean = backgroundStyleResId != 0

    // original: k
    fun isToggleStyle(): Boolean = isToggleStyle

    // original: l
    fun isAccentStyle(): Boolean = isAccentStyle

    // original: m
    fun isDestructiveStyle(): Boolean = isDestructiveStyle

    // original: n
    fun setCustomTitle(title: String): MenuItemModel {
        this.customTitle = title
        return this
    }

    // original: o
    fun setIcon(iconResId: Int): MenuItemModel {
        this.iconResId = iconResId
        return this
    }
}
