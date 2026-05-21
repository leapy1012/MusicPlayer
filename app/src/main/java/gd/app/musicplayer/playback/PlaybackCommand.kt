package gd.app.musicplayer.playback

import gd.app.musicplayer.domain.model.Music

sealed interface PlaybackCommand {

    data class PlayFromQueue(
        val queue: List<Music>,
        val index: Int
    ) : PlaybackCommand

    data class Enqueue(
        val queue: List<Music>
    ) : PlaybackCommand

    data class PlayNextItems(
        val queue: List<Music>
    ) : PlaybackCommand

    data class ReplaceQueue(
        val queue: List<Music>,
        val index: Int
    ) : PlaybackCommand

    data class SeekTo(
        val positionMs: Int
    ) : PlaybackCommand

    data class SetStopAfterCurrentTrack(
        val enabled: Boolean
    ) : PlaybackCommand

    data class PlayIndex(
        val index: Int
    ) : PlaybackCommand

    data class RemoveQueueItem(
        val index: Int
    ) : PlaybackCommand

    data class MoveQueueItem(
        val fromIndex: Int,
        val toIndex: Int
    ) : PlaybackCommand

    data class RefreshEditedTrack(
        val track: Music
    ) : PlaybackCommand

    data class RefreshEditedTracks(
        val tracks: List<Music>
    ) : PlaybackCommand

    data object TogglePlayPause : PlaybackCommand
    data object Play : PlaybackCommand
    data object Pause : PlaybackCommand
    data object Next : PlaybackCommand
    data object Previous : PlaybackCommand
    data object ClearQueue : PlaybackCommand
    data object Stop : PlaybackCommand
    data object ApplyAudioEffects : PlaybackCommand
    data object ApplyPlaybackTuning : PlaybackCommand
    data object RefreshNotificationStyle : PlaybackCommand
    data object RestartCurrentTrack : PlaybackCommand
    data object ChangeMode : PlaybackCommand
    data object SetShuffleAllMode : PlaybackCommand
    data object ToggleFavorite : PlaybackCommand
}
