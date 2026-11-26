package gd.app.musicplayer.playback.desktop

import android.app.Activity
import android.app.Application
import android.view.View
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import gd.app.musicplayer.R
import gd.app.musicplayer.core.datastore.DesktopLyricPreference
import gd.app.musicplayer.core.datastore.TrackLyricPreferenceStore
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.playback.queue.MusicPlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class,manifest=Config.NONE)
@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
class DesktopLyricsOverlayTest {
    @get:Rule val folder=TemporaryFolder()
    @Test fun firstOverlayUsesPlaybackPublishedBeforeItWasEnabled() {
        ShadowSettings.setCanDrawOverlays(true)
        val c=Robolectric.buildActivity(Activity::class.java)
        c.get().setTheme(R.style.AppTheme)
        val activity=c.setup().get()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val store=PreferenceDataStoreFactory.create(scope=scope) { java.io.File(folder.root,"lyrics.preferences_pb") }
        val overlay=DesktopLyricsOverlayController(activity,scope,TrackLyricPreferenceStore(store),
            object:DesktopLyricsOverlayController.Callbacks {
                override fun previous()=Unit
                override fun next()=Unit
                override fun togglePlayPause()=Unit
                override fun cyclePlaybackMode()=Unit
                override fun toggleFavorite()=Unit
                override fun closeDesktopLyrics()=Unit
                override fun lockDesktopLyrics()=Unit
                override fun updatePreference(presetColorIndex:Int?,currentColorProgress:Int?,normalColorProgress:Int?,alpha:Float?,textSize:Int?,y:Int?)=Unit
                override fun currentPlaybackMode()=0
            })
        try {
            overlay.renderAppForeground(true)
            overlay.renderPlaybackState(MusicPlaybackState(currentTrack=Music(42,"Test","Artist","Album","0",0),isPlaying=true))
            overlay.renderPreference(DesktopLyricPreference(visible=true))
            overlay.renderAppForeground(false)
            val root=DesktopLyricsOverlayController::class.java.getDeclaredField("rootView").apply { isAccessible=true }.get(overlay) as View
            assertNotNull(root.parent)
            assertTrue("Opening an overlay must replay the current play/pause state",root.findViewById<View>(R.id.desk_lrc_play_pause).isSelected)
            overlay.renderAppForeground(true)
            assertNull(root.parent)
            overlay.renderAppForeground(false)
            assertNotNull(root.parent)
            assertTrue(root.findViewById<View>(R.id.desk_lrc_play_pause).isSelected)
        } finally { overlay.destroy();scope.cancel();c.pause().stop().destroy() }
    }
}
