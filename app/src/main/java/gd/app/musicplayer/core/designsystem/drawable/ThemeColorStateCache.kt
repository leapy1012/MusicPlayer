package gd.app.musicplayer.core.designsystem.drawable

import android.content.res.ColorStateList
import android.util.SparseArray

object ThemeColorStateCache {

    private val cache =
        SparseArray<ColorStateList>()

    fun single(
        color: Int
    ): ColorStateList {

        return cache[color]
            ?: ColorStateList.valueOf(color).also {
                cache.put(color, it)
            }
    }

    fun clear() {
        cache.clear()
    }
}