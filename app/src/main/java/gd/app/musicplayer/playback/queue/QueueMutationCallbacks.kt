package gd.app.musicplayer.playback.queue

import gd.app.musicplayer.domain.model.Music

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
        queue: List<Music>,
        currentIndex: Int,
        fromAutoTransition: Boolean
    ): Int?

    fun resolvePreviousIndex(
        queue: List<Music>,
        currentIndex: Int,
        shouldRestartCurrent: Boolean
    ): Int?

    fun onAutoTransitionReachedQueueEnd()

    fun markPreviousNavigated()

    fun onQueueInitialized(queue: List<Music>, currentIndex: Int)

    fun onQueueCleared()

    fun onTracksAppended(queue: List<Music>, added: List<Music>)

    fun onTracksInsertedForNext(added: List<Music>)

    fun onQueueMutated(queue: List<Music>, currentIndex: Int)

    fun currentTrackDurationMs(): Int

    fun persistCurrentTrackProgress(positionMs: Int)

    fun persistSessionFromCurrentStateAsync()

    fun resumePlaybackInternal()
}
