package gd.app.musicplayer.playback.service

import android.app.Service
import android.content.res.Configuration
import gd.app.musicplayer.playback.shutdown.ShutdownOptions
import gd.app.musicplayer.playback.state.PublishReason

/**
 * Owns service lifecycle orchestration and resource teardown order.
 *
 * Android callbacks stay in [MusicPlaybackService], but this class keeps the
 * lifecycle policy explicit and testable.
 */
class PlaybackLifecycleController(
    private val callbacks: Callbacks
) {

    interface Callbacks {
        fun setServiceRunning(running: Boolean)
        fun configurePlayer()
        fun configureControllers()
        fun observePreferences()
        fun observeCurrentTrackArtwork()
        fun observeCurrentTrackFavorite()
        fun setSleepTimerPlaybackProvider()
        fun createNotificationChannel()
        fun restoreLastSessionIntoRuntimeStateIfNeeded()
        fun registerScreenOffReceiver()
        fun startProgressTicker()

        fun isEffectivelyPlaying(): Boolean
        fun hasCurrentQueueItem(): Boolean
        fun stopSelfForStartId(startId: Int)
        fun publishPlaybackStateFromLifecycle(
            reason: PublishReason,
            forceNotification: Boolean
        )

        fun updateNightModeFromConfiguration(configuration: Configuration)
        fun updateNotificationFromLifecycle(force: Boolean)
        fun notifyOverlayConfigurationChanged()

        fun handleTaskRemovedWhilePlaying()
        fun shutdownPlayback(options: ShutdownOptions)
        fun releasePlaybackResources()
    }

    fun onCreate() {
        callbacks.setServiceRunning(true)
        callbacks.configurePlayer()
        callbacks.configureControllers()
        callbacks.observePreferences()
        callbacks.observeCurrentTrackArtwork()
        callbacks.observeCurrentTrackFavorite()
        callbacks.setSleepTimerPlaybackProvider()
        callbacks.createNotificationChannel()
        callbacks.restoreLastSessionIntoRuntimeStateIfNeeded()
        callbacks.registerScreenOffReceiver()
        callbacks.startProgressTicker()
    }

    fun handleEmptyStartCommand(startId: Int): Int {
        if (!callbacks.isEffectivelyPlaying() && !callbacks.hasCurrentQueueItem()) {
            callbacks.setServiceRunning(false)
            callbacks.stopSelfForStartId(startId)
            return Service.START_NOT_STICKY
        }

        callbacks.publishPlaybackStateFromLifecycle(
            reason = PublishReason.UserAction,
            forceNotification = false
        )

        return Service.START_STICKY
    }

    fun onTaskRemoved() {
        if (callbacks.isEffectivelyPlaying()) {
            callbacks.handleTaskRemovedWhilePlaying()
            return
        }

        callbacks.setServiceRunning(false)
        callbacks.shutdownPlayback(ShutdownOptions.TaskRemovedWhenPaused)
    }

    fun onConfigurationChanged(configuration: Configuration) {
        callbacks.updateNightModeFromConfiguration(configuration)
        callbacks.updateNotificationFromLifecycle(force = true)
        callbacks.notifyOverlayConfigurationChanged()
    }

    fun onDestroy() {
        callbacks.releasePlaybackResources()
    }
}

