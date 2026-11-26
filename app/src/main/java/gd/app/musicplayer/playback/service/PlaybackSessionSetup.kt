package gd.app.musicplayer.playback.service

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.ui.shell.MainActivity

internal fun MusicPlaybackService.buildMedia3Session(): androidx.media3.session.MediaLibraryService.MediaLibrarySession {
    val sessionActivity = PendingIntent.getActivity(
        this,
        MEDIA3_SESSION_ACTIVITY_REQUEST_CODE,
        Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(
                MainActivity.EXTRA_EXPAND_PLAYER,
                true
            )
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    return androidx.media3.session.MediaLibraryService.MediaLibrarySession.Builder(
        this, sessionPlayer, PlaybackLibrarySessionCallback(this)
    )
        .setSessionActivity(sessionActivity)
        .build()
}

internal fun Int.isMedia3TransportNavigationCommand(): Boolean {
    return this == Player.COMMAND_SEEK_TO_NEXT ||
            this == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
            this == Player.COMMAND_SEEK_TO_PREVIOUS ||
            this == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
}

@OptIn(UnstableApi::class)
internal fun MusicPlaybackService.updateMedia3CommandButtons() {
    val session = media3Session ?: return
    val buttons = buildMedia3CommandButtons()

    session.setCustomLayout(buttons)
    session.setMediaButtonPreferences(buttons)
}

internal fun MusicPlaybackService.buildMedia3CommandButtons(): List<CommandButton> =
    gd.app.musicplayer.playback.notification.musicSessionButtons(
        this, queue.getOrNull(currentIndex)?.playlistId == MusicSet.FAVORITES
    )
