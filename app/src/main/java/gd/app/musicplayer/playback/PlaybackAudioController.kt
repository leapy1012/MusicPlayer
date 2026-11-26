package gd.app.musicplayer.playback

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.di.ApplicationScope
import gd.app.musicplayer.playback.command.PlaybackCommand
import gd.app.musicplayer.playback.command.PlaybackCommandDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** UI/headset client. The playback service is the sole transport and player owner. */
@Singleton
class PlaybackAudioController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatcher: PlaybackCommandDispatcher,
    private val startupInitializer: dagger.Lazy<PlaybackStartupInitializer>,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    fun ensureInitialized() { scope.launch { startupInitializer.get().initialize() } }
    fun playOrPause() = dispatch(PlaybackCommand.TogglePlayPause)
    fun play() = dispatch(PlaybackCommand.Play)
    fun pause() = dispatch(PlaybackCommand.Pause)
    fun playNext() = dispatch(PlaybackCommand.Next)
    fun playPrevious() = dispatch(PlaybackCommand.Previous)
    private fun dispatch(command: PlaybackCommand) = dispatcher.dispatch(context, command)
}
