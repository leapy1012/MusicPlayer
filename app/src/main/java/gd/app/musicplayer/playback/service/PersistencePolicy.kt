package gd.app.musicplayer.playback.service

import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.playback.state.PlaybackSnapshot

internal sealed interface PersistenceOp {
    data class PersistSnapshot(
        val snapshot: PlaybackSnapshot,
        val persistQueue: Boolean
    ) : PersistenceOp

    data class PersistProgress(
        val track: Music?,
        val positionMs: Long,
        val currentIndex: Int
    ) : PersistenceOp
}

internal fun resolvePersistencePlan(event: PersistenceEvent): List<PersistenceOp> {
    return when (event) {
        is PersistenceEvent.TrackProgress -> {
            listOf(
                PersistenceOp.PersistProgress(
                    track = event.track,
                    positionMs = event.positionMs,
                    currentIndex = event.currentIndex
                )
            )
        }

        is PersistenceEvent.PlaybackSnapshot -> {
            listOf(
                PersistenceOp.PersistSnapshot(
                    snapshot = event.snapshot,
                    persistQueue = event.persistQueue
                )
            )
        }

        is PersistenceEvent.QueueMutation -> {
            listOf(
                PersistenceOp.PersistSnapshot(
                    snapshot = event.snapshot,
                    persistQueue = true
                )
            )
        }

        is PersistenceEvent.Pause -> {
            listOf(
                PersistenceOp.PersistProgress(
                    track = event.snapshot.currentTrack,
                    positionMs = event.snapshot.positionMs,
                    currentIndex = event.snapshot.currentIndex
                ),
                PersistenceOp.PersistSnapshot(
                    snapshot = event.snapshot,
                    persistQueue = false
                )
            )
        }

        is PersistenceEvent.Transition -> {
            listOf(
                PersistenceOp.PersistSnapshot(
                    snapshot = event.snapshot,
                    persistQueue = false
                )
            )
        }

        is PersistenceEvent.Seek -> {
            listOf(
                PersistenceOp.PersistProgress(
                    track = event.snapshot.currentTrack,
                    positionMs = event.snapshot.positionMs,
                    currentIndex = event.snapshot.currentIndex
                )
            )
        }

        is PersistenceEvent.Stop -> {
            listOf(
                PersistenceOp.PersistProgress(
                    track = event.snapshot.currentTrack,
                    positionMs = event.snapshot.positionMs,
                    currentIndex = event.snapshot.currentIndex
                ),
                PersistenceOp.PersistSnapshot(
                    snapshot = event.snapshot,
                    persistQueue = event.clearQueue
                )
            )
        }
    }
}
