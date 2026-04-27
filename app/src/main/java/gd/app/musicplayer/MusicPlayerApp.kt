package gd.app.musicplayer

import android.app.Application
import gd.app.musicplayer.core.di.AppContainer
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MusicPlayerApp : Application() {
    val appContainer: AppContainer by lazy {
        AppContainer(this)
    }
}
