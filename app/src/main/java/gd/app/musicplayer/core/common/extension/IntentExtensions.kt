package gd.app.musicplayer.core.common.extension

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import kotlin.collections.joinToString
import kotlin.collections.orEmpty

inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key)
    }
}

fun Intent.externalIntentKey(): String {
    val streamValue = when (action) {
        Intent.ACTION_SEND -> {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)?.toString()
            } else {
                @Suppress("DEPRECATION")
                (getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)?.toString()
            }
        }
        Intent.ACTION_SEND_MULTIPLE -> {
            val items = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            items.orEmpty().joinToString(",") { it.toString() }
        }
        else -> dataString.orEmpty()
    }

    return "${action.orEmpty()}|${type.orEmpty()}|$streamValue"
}

fun Intent?.isExternalAudioIntent(): Boolean {
    if (this == null) return false
    return when (action) {
        Intent.ACTION_VIEW,
        Intent.ACTION_SEND,
        Intent.ACTION_SEND_MULTIPLE,
        "android.intent.action.MUSIC_PLAYER" -> true
        else -> false
    }
}

fun Intent.extractExternalAudioUris(): List<Uri> {
    return when (action) {
        Intent.ACTION_VIEW -> listOfNotNull(data)
        Intent.ACTION_SEND -> listOfNotNull(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                getParcelableExtra(Intent.EXTRA_STREAM)
            }
        )
        Intent.ACTION_SEND_MULTIPLE -> {
            val items = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            items.orEmpty()
        }
        else -> listOfNotNull(data)
    }
}