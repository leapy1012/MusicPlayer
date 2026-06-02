package gd.app.musicplayer.playback.service

import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.playback.state.PlaybackSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistencePolicyTest {

    @Test
    fun `pause persists progress and snapshot without queue`() {
        val snapshot = snapshot(positionMs = 42L)

        val ops = resolvePersistencePlan(
            PersistenceEvent.Pause(snapshot)
        )

        assertEquals(2, ops.size)
        assertTrue(ops[0] is PersistenceOp.PersistProgress)
        assertTrue(ops[1] is PersistenceOp.PersistSnapshot)
        assertFalse((ops[1] as PersistenceOp.PersistSnapshot).persistQueue)
    }

    @Test
    fun `queue mutation persists snapshot with queue`() {
        val snapshot = snapshot(positionMs = 15L)

        val ops = resolvePersistencePlan(
            PersistenceEvent.QueueMutation(snapshot)
        )

        assertEquals(1, ops.size)
        assertTrue(ops[0] is PersistenceOp.PersistSnapshot)
        assertTrue((ops[0] as PersistenceOp.PersistSnapshot).persistQueue)
    }

    @Test
    fun `stop persists progress then snapshot using clear queue flag`() {
        val snapshot = snapshot(positionMs = 100L)

        val clearOps = resolvePersistencePlan(
            PersistenceEvent.Stop(snapshot = snapshot, clearQueue = true)
        )
        val keepOps = resolvePersistencePlan(
            PersistenceEvent.Stop(snapshot = snapshot, clearQueue = false)
        )

        assertEquals(2, clearOps.size)
        assertTrue(clearOps[0] is PersistenceOp.PersistProgress)
        assertTrue((clearOps[1] as PersistenceOp.PersistSnapshot).persistQueue)

        assertEquals(2, keepOps.size)
        assertFalse((keepOps[1] as PersistenceOp.PersistSnapshot).persistQueue)
    }

    @Test
    fun `transition persists snapshot only without queue`() {
        val snapshot = snapshot(positionMs = 55L)

        val ops = resolvePersistencePlan(
            PersistenceEvent.Transition(snapshot)
        )

        assertEquals(1, ops.size)
        assertTrue(ops[0] is PersistenceOp.PersistSnapshot)
        assertFalse((ops[0] as PersistenceOp.PersistSnapshot).persistQueue)
    }

    private fun snapshot(positionMs: Long): PlaybackSnapshot {
        val track = Music(
            id = 1L,
            title = "title",
            artist = "artist",
            album = "album",
            albumId = "album-id",
            playlistId = 1L
        )
        return PlaybackSnapshot(
            queue = listOf(track),
            currentIndex = 0,
            currentTrack = track,
            positionMs = positionMs,
            durationMs = 200L,
            audioSessionId = 1
        )
    }
}
