package gd.app.musicplayer.playback.notification

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import gd.app.musicplayer.R
import gd.app.musicplayer.playback.command.PlaybackServiceActions
import gd.app.musicplayer.playback.service.MusicPlaybackService

@UnstableApi
internal fun musicSessionButtons(context: Context, favorite: Boolean): List<CommandButton> = buildList {
    fun button(action: String, label: Int, icon: Int, slot: Int) = CommandButton.Builder()
        .setSessionCommand(SessionCommand(action, Bundle.EMPTY))
        .setDisplayName(context.getString(label)).setCustomIconResId(icon).setSlots(slot).build()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(button(PlaybackServiceActions.ACTION_PREVIOUS, R.string.notification_previous_track,
            R.drawable.ic_player_previous_outline, CommandButton.SLOT_BACK))
        add(button(PlaybackServiceActions.ACTION_NEXT, R.string.notification_next_track,
            R.drawable.ic_player_next_outline, CommandButton.SLOT_FORWARD))
    }
    add(button(MusicPlaybackService.MEDIA3_COMMAND_TOGGLE_FAVORITE,
        if (favorite) R.string.music_unfavorite else R.string.favorite,
        if (favorite) R.drawable.vector_notify_favorite else R.drawable.vector_notify_unfavorite,
        CommandButton.SLOT_OVERFLOW))
    add(button(MusicPlaybackService.MEDIA3_COMMAND_CLOSE_NOTIFICATION, R.string.operation_stop,
        R.drawable.vector_notify_close, CommandButton.SLOT_OVERFLOW))
}
