package gd.app.musicplayer.playback.command
import android.content.Intent
import android.os.Build
import android.os.Parcelable
import gd.app.musicplayer.domain.model.Music

fun Intent?.musicListExtraCompat(key: String): List<Music> {
    if (this == null) return emptyList()
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayListExtra(key, Music::class.java).orEmpty()
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayListExtra<Music>(key).orEmpty()
    }
}

inline fun <reified T : Parcelable> Intent?.parcelableExtraCompat(key: String): T? {
    if (this == null) return null
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key)
    }
}
