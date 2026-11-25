package gd.app.musicplayer.playback.notification

import android.app.Application
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.test.core.app.ApplicationProvider
import gd.app.musicplayer.R
import gd.app.musicplayer.playback.command.PlaybackServiceActions
import gd.app.musicplayer.playback.service.MusicPlaybackService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
class MediaSessionActionsTest {
    private fun buttons(favorite: Boolean = false) =
        musicSessionButtons(ApplicationProvider.getApplicationContext(), favorite)

    @Test fun modernSystemPlayerReceivesSkipIconsInPreviousAndNextSlots() {
        val buttons = buttons()
        assertEquals(listOf(PlaybackServiceActions.ACTION_PREVIOUS, PlaybackServiceActions.ACTION_NEXT,
            MusicPlaybackService.MEDIA3_COMMAND_TOGGLE_FAVORITE, MusicPlaybackService.MEDIA3_COMMAND_CLOSE_NOTIFICATION),
            buttons.map { it.sessionCommand!!.customAction })
        assertEquals(R.drawable.ic_player_previous_outline, buttons[0].iconResId)
        assertEquals(R.drawable.ic_player_next_outline, buttons[1].iconResId)
        assertEquals(CommandButton.SLOT_BACK, buttons[0].slots[0])
        assertEquals(CommandButton.SLOT_FORWARD, buttons[1].slots[0])
        assertEquals(CommandButton.SLOT_OVERFLOW, buttons[2].slots[0])
        assertEquals(CommandButton.SLOT_OVERFLOW, buttons[3].slots[0])
    }

    @Test fun favoriteToggleKeepsSkipSlotsAndUsesTheSelectedStar() {
        val buttons = buttons(favorite = true)
        assertEquals(PlaybackServiceActions.ACTION_PREVIOUS, buttons[0].sessionCommand!!.customAction)
        assertEquals(PlaybackServiceActions.ACTION_NEXT, buttons[1].sessionCommand!!.customAction)
        assertEquals(R.drawable.vector_notify_favorite, buttons[2].iconResId)
    }

    @Test @Config(sdk = [29])
    fun olderSystemPlayersDoNotReceiveDuplicateSkipButtons() {
        assertEquals(listOf(MusicPlaybackService.MEDIA3_COMMAND_TOGGLE_FAVORITE,
            MusicPlaybackService.MEDIA3_COMMAND_CLOSE_NOTIFICATION),
            buttons().map { it.sessionCommand!!.customAction })
    }
}
