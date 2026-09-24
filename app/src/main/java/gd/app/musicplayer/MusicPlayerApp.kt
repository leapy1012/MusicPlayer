package gd.app.musicplayer

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import gd.app.musicplayer.core.common.AppForegroundTracker
import gd.app.musicplayer.core.datastore.MusicDataStore
import gd.app.musicplayer.core.datastore.SoundEffectPreferences
import gd.app.musicplayer.core.mediastore.MediaStoreLibraryObserver
import gd.app.musicplayer.playback.headset.HeadsetAutomationManager
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class MusicPlayerApp : Application() {

    @Inject lateinit var mediaStoreLibraryObserver: MediaStoreLibraryObserver
    @Inject lateinit var headsetAutomationManager: HeadsetAutomationManager
    @Inject lateinit var musicDataStore: MusicDataStore
    @Inject lateinit var soundEffectPreferences: SoundEffectPreferences

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AppForegroundTracker.register(this)
        mediaStoreLibraryObserver.register()
        headsetAutomationManager.initialize(this)
        appScope.launch {
            runCatching {
                musicDataStore.syncBooleansToSharedPreferences()
                soundEffectPreferences.syncBooleansToSharedPreferences()
            }
        }
    }
}
