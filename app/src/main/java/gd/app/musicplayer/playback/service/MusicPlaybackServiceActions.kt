package gd.app.musicplayer.playback.service

import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.playback.command.PlaybackCommandHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class MusicPlaybackServiceActions(
    private val service: MusicPlaybackService,
    private val serviceScope: CoroutineScope
) : PlaybackCommandHandler.Callbacks {

    override fun refreshNotificationStyle() {
        serviceScope.launch {
            service.refreshNotificationStyleFromCommand()
        }
    }

    override fun exitService() {
        service.exitService()
    }

    override fun pauseAndPersistForNotificationClose() {
        service.pauseAndPersistForNotificationClose()
    }

    override fun togglePlayPause() {
        service.togglePlayPause()
    }

    override fun resumePlayback() {
        service.resumePlayback()
    }

    override fun pausePlayback() {
        service.pausePlayback()
    }

    override fun playNext() {
        service.playNext()
    }

    override fun playPrevious() {
        service.playPrevious()
    }

    override fun stopPlayback() {
        service.stopPlayback()
    }

    override fun stopPlaybackWithoutClearingQueue() {
        service.stopPlaybackWithoutClearingQueue()
    }

    override fun restartCurrentTrack() {
        service.restartCurrentTrack()
    }

    override fun clearQueueKeepingNotification() {
        service.clearQueueKeepingNotification()
    }

    override fun cyclePlaybackMode() {
        service.cyclePlaybackModeFromCommand()
    }

    override fun setPlaybackMode(mode: Int) {
        service.setPlaybackModeFromCommand(mode)
    }

    override fun toggleCurrentFavorite() {
        service.toggleCurrentFavorite()
    }

    override fun setCurrentFavorite(isFavorite: Boolean) {
        service.setCurrentFavorite(isFavorite)
    }

    override fun playIndex(index: Int) {
        service.playIndexFromCommand(index)
    }

    override fun playFromQueue(
        queue: List<Music>,
        index: Int
    ) {
        service.handlePlayFromQueue(
            incomingQueue = queue,
            incomingIndex = index
        )
    }

    override fun enqueue(queue: List<Music>) {
        service.handleEnqueue(queue)
    }

    override fun playNextQueue(queue: List<Music>) {
        service.handlePlayNextQueue(queue)
    }

    override fun replaceQueue(
        queue: List<Music>,
        index: Int
    ) {
        service.replaceQueue(
            newQueue = queue,
            requestedIndex = index
        )
    }

    override fun removeQueueItem(index: Int) {
        service.removeQueueItem(index)
    }

    override fun moveQueueItem(
        fromIndex: Int,
        toIndex: Int
    ) {
        service.moveQueueItem(
            fromIndex = fromIndex,
            toIndex = toIndex
        )
    }

    override fun updateTrackMetadata(music: Music) {
        service.updateEditedTrackMetadata(music)
    }

    override fun updateTracksMetadata(music: List<Music>) {
        service.updateEditedTracksMetadata(music)
    }

    override fun seekTo(positionMs: Int) {
        service.seekTo(positionMs)
    }

    override fun setStopAfterCurrentTrack(enabled: Boolean) {
        service.updateStopAfterCurrentTrackMode(enabled)
    }

    override fun applyAudioEffects() {
        service.applyAudioEffectsFromPreferences()
    }

    override fun applyPlaybackTuning() {
        service.applyPlaybackTuningFromCommand()
    }

    override fun setDesktopLyricsLocked(locked: Boolean) {
        service.setDesktopLyricsLockedFromCommand(locked)
    }

    override fun currentMusic(): Music? {
        return service.currentMusicForCommand()
    }
}
