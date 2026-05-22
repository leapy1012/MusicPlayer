package gd.app.musicplayer

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import gd.app.musicplayer.core.common.AppForegroundTracker
import gd.app.musicplayer.data.local.mediastore.MediaStoreLibraryObserver
import gd.app.musicplayer.playback.HeadsetAutomationManager
import javax.inject.Inject

@HiltAndroidApp
class MusicPlayerApp : Application() {

    @Inject lateinit var mediaStoreLibraryObserver: MediaStoreLibraryObserver
    @Inject lateinit var headsetAutomationManager: HeadsetAutomationManager

    override fun onCreate() {
        super.onCreate()
        AppForegroundTracker.register(this)
        mediaStoreLibraryObserver.register()
        headsetAutomationManager.initialize(this)
    }
}
