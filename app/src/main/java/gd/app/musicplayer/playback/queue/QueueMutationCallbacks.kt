package gd.app.musicplayer.playback.queue

interface QueueMutationCallbacks {

    fun markPlaybackRestored()

    fun requestAudioFocus(): Boolean

    fun isEffectivelyPlaying(): Boolean

    fun currentPlayerPositionMs(): Long

    fun updateNotificationSessionQueue()

    fun refreshArtworkAndSession(force: Boolean)

    fun publishQueueChanged(forceNotification: Boolean = false)

    fun publishPlayerEvent(forceNotification: Boolean = false)

    fun onQueueBecameEmpty()

    fun onReplaceWithEmptyQueue()

    fun playerPlaybackStateIsNotIdle(): Boolean

    fun resetPlaybackStatistics()

    fun resetTimedTransition()

    fun applyVolumeForPlaybackStart(playWhenReady: Boolean)

    fun resolveNextIndex(
        queueSize: Int,
        currentIndex: Int,
        fromAutoTransition: Boolean
    ): Int?

    fun resolvePreviousIndex(
        queueSize: Int,
        currentIndex: Int,
        shouldRestartCurrent: Boolean
    ): Int?

    fun onAutoTransitionReachedQueueEnd()

    fun currentPlayerPositionIsAfterPreviousRestartWindow(): Boolean

    fun seekCurrentToStart()

    fun currentTrackDurationMs(): Int

    fun persistForSeek(positionMs: Int)

    fun resumePlaybackInternal()
}
