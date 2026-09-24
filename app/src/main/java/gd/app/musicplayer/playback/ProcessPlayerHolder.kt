package gd.app.musicplayer.playback

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.playback.effects.StereoBalanceAudioProcessor
import gd.app.musicplayer.playback.player.MusicPlayerFactory
import gd.app.musicplayer.playback.player.PlaybackEngine
import gd.app.musicplayer.playback.player.PlayerEventHandler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-scoped players — mirrors original BassPlayer ([u6.a]) living inside [y6.y],
 * not inside MusicPlayService. Service attaches after play for notification only.
 */
@Singleton
class ProcessPlayerHolder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val musicPlayerFactory: MusicPlayerFactory
) {
    private val lock = Any()
    private val engine = PlaybackEngine(context, musicPlayerFactory)

    private var components: PlaybackEngine.Components? = null

    @Volatile
    var externalCallbacks: PlaybackEngine.Callbacks? = null

    private val bridgeCallbacks = object : PlaybackEngine.Callbacks {
        override fun onPlayerReady() {
            externalCallbacks?.onPlayerReady()
        }

        override fun onTrackEnded() {
            externalCallbacks?.onTrackEnded()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            externalCallbacks?.onIsPlayingChanged(isPlaying)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean) {
            externalCallbacks?.onPlayWhenReadyChanged(playWhenReady)
        }

        override fun onMediaItemTransition(reason: Int) {
            externalCallbacks?.onMediaItemTransition(reason)
        }

        override fun onPlayerError(error: PlaybackException) {
            externalCallbacks?.onPlayerError(error)
        }
    }

    val isCreated: Boolean
        get() = synchronized(lock) { components != null }

    fun getOrCreate(): PlaybackEngine.Components {
        synchronized(lock) {
            val existing = components
            if (existing != null) return existing

            val created = engine.create(bridgeCallbacks)
            components = created
            return created
        }
    }

    fun ensureCrossfadePlayer(): ExoPlayer {
        return getOrCreate().ensureCrossfadePlayer()
    }

    /**
     * Original [u6.e.v]: promote incoming crossfade player to primary.
     * @return outgoing (former primary) for the caller to stop/release, or null
     */
    fun promoteCrossfadeToPrimary(): ExoPlayer? {
        synchronized(lock) {
            val current = components ?: return null
            return current.promoteCrossfadeToPrimary()
        }
    }

    fun playerOrNull(): ExoPlayer? = synchronized(lock) { components?.player }

    fun crossfadePlayerOrNull(): ExoPlayer? =
        synchronized(lock) { components?.crossfadePlayerOrNull }

    fun release() {
        synchronized(lock) {
            val current = components ?: return
            engine.release(
                player = current.player,
                crossfadePlayer = current.crossfadePlayerOrNull,
                playerEventHandler = current.playerEventHandler
            )
            components = null
            externalCallbacks = null
        }
    }

    fun componentsOrNull(): PlaybackEngine.Components? = synchronized(lock) { components }

    fun stereoBalanceOrNull(): StereoBalanceAudioProcessor? =
        synchronized(lock) { components?.stereoBalanceAudioProcessor }

    fun eventHandlerOrNull(): PlayerEventHandler? =
        synchronized(lock) { components?.playerEventHandler }
}
