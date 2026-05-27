package gd.app.musicplayer.playback.service

import android.content.res.Configuration
import gd.app.musicplayer.playback.shutdown.ShutdownOptions
import gd.app.musicplayer.playback.state.PublishReason
import gd.app.musicplayer.playback.timer.SleepTimerManager

/** Bridges lifecycle orchestration to [MusicPlaybackService] implementation details. */
class PlaybackLifecycleCallbacks(
    private val service: MusicPlaybackService
) : PlaybackLifecycleController.Callbacks {

    override fun setServiceRunning(running: Boolean) {
        service.setServiceRunning(running)
    }

    override fun configurePlayer() {
        service.configurePlayer()
    }

    override fun configureControllers() {
        service.configureControllers()
    }

    override fun observePreferences() {
        service.observePreferences()
    }

    override fun observeCurrentTrackArtwork() {
        service.observeCurrentTrackArtwork()
    }

    override fun observeCurrentTrackFavorite() {
        service.observeCurrentTrackFavorite()
    }

    override fun setSleepTimerPlaybackProvider() {
        SleepTimerManager.setPlaybackActiveProvider(service::isEffectivelyPlaying)
    }

    override fun createNotificationChannel() {
        service.createNotificationChannelFromLifecycle()
    }

    override fun restoreLastSessionIntoRuntimeStateIfNeeded() {
        service.restoreLastSessionIntoRuntimeStateIfNeeded()
    }

    override fun registerScreenOffReceiver() {
        service.registerScreenOffReceiver()
    }

    override fun startProgressTicker() {
        service.startProgressTickerFromLifecycle()
    }

    override fun isEffectivelyPlaying(): Boolean {
        return service.isEffectivelyPlaying()
    }

    override fun hasCurrentQueueItem(): Boolean {
        return service.hasCurrentQueueItem()
    }

    override fun stopSelfForStartId(startId: Int) {
        service.stopSelfForStartId(startId)
    }

    override fun publishPlaybackStateFromLifecycle(
        reason: PublishReason,
        forceNotification: Boolean
    ) {
        service.publishPlaybackStateFromLifecycle(
            reason = reason,
            forceNotification = forceNotification
        )
    }

    override fun updateNightModeFromConfiguration(configuration: Configuration) {
        service.updateNightModeFromConfiguration(configuration)
    }

    override fun updateNotificationFromLifecycle(force: Boolean) {
        service.updateNotificationFromLifecycle(force)
    }

    override fun notifyOverlayConfigurationChanged() {
        service.notifyOverlayConfigurationChanged()
    }

    override fun handleTaskRemovedWhilePlaying() {
        service.handleTaskRemovedWhilePlaying()
    }

    override fun shutdownPlayback(options: ShutdownOptions) {
        service.shutdownPlayback(options)
    }

    override fun releasePlaybackResources() {
        service.releasePlaybackResources()
    }
}

