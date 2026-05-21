package gd.app.musicplayer.ui.home

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

data class MainItem(
    val category: MainCategory,
    @param:StringRes val titleRes: Int,
    @param:DrawableRes val iconRes: Int,
    @param:ColorInt val bgColor: Int,
    val count: Int
)

enum class MainCategory {
    Library,
    Folders,
    Favorites,
    RecentlyPlayed,
    RecentlyAdded,
    MostPlayed
}
