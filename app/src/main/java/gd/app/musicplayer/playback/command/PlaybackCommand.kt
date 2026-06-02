package gd.app.musicplayer.playback.command
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

    object TogglePlayPause : PlaybackCommand
    object Play : PlaybackCommand
    object Pause : PlaybackCommand
    object Next : PlaybackCommand
    object Previous : PlaybackCommand
    object ClearQueue : PlaybackCommand
    object Stop : PlaybackCommand
    object ApplyAudioEffects : PlaybackCommand
    object ApplyPlaybackTuning : PlaybackCommand
    object RefreshNotificationStyle : PlaybackCommand
    object RestartCurrentTrack : PlaybackCommand
    object ChangeMode : PlaybackCommand
    object SetShuffleAllMode : PlaybackCommand
    object ToggleFavorite : PlaybackCommand
}
