package gd.app.musicplayer.core.common.extension

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Parcelable

const val ACTION_MUSIC_PLAYER = "android.intent.action.MUSIC_PLAYER"

private val EXTERNAL_AUDIO_ACTIONS = setOf(
    Intent.ACTION_SEND,
    Intent.ACTION_SEND_MULTIPLE,
    Intent.ACTION_VIEW,
    ACTION_MUSIC_PLAYER
)

val MEDIA_OPEN_ACTIONS = setOf(
    Intent.ACTION_SEND,
    Intent.ACTION_VIEW,
    ACTION_MUSIC_PLAYER
)

inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key)
    }
}

inline fun <reified T : Parcelable> Intent.parcelableArrayList(
    key: String
): ArrayList<T>? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayListExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayListExtra(key)
    }
}

fun Intent.externalIntentKey(): String {
    val streamValue = when (action) {
        Intent.ACTION_SEND -> streamUri()?.toString()
        Intent.ACTION_SEND_MULTIPLE -> streamUris().joinToString(separator = ",")
        else -> dataString.orEmpty()
    }

    return "${action.orEmpty()}|${type.orEmpty()}|$streamValue"
}

fun Intent?.isExternalAudioIntent(): Boolean {
    return this != null && action in EXTERNAL_AUDIO_ACTIONS
}

fun Intent.extractExternalAudioUris(): List<Uri> {
    return when (action) {
        Intent.ACTION_VIEW -> listOfNotNull(data)
        Intent.ACTION_SEND -> listOfNotNull(streamUri())
        Intent.ACTION_SEND_MULTIPLE -> streamUris()
        else -> listOfNotNull(data)
    }
}

fun Intent?.isMediaOpenIntent(): Boolean {
    return this != null &&
            !wasLaunchedFromHistory() &&
            action in MEDIA_OPEN_ACTIONS
}

fun Intent.wasLaunchedFromHistory(): Boolean {
    return flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
}

private fun Intent.streamUri(): Uri? {
    return parcelable(Intent.EXTRA_STREAM)
}

private fun Intent.streamUris(): List<Uri> {
    return parcelableArrayList<Uri>(Intent.EXTRA_STREAM).orEmpty()
}