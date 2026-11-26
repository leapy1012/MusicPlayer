package gd.app.musicplayer.playback.service

import org.junit.Assert.*
import org.junit.Test

class PlaybackRequestGateTest {
    @Test fun `pause invalidates play waiting for storage`() {
        val gate = PlaybackRequestGate()
        val loading = gate.play()
        gate.cancel()
        assertFalse(gate.isCurrent(loading))
        assertFalse(gate.wantsPlayback)
    }
    @Test fun `a newer selection wins over an older play request`() {
        val gate = PlaybackRequestGate()
        val older = gate.play()
        val newer = gate.play()
        assertFalse(gate.isCurrent(older))
        assertTrue(gate.isCurrent(newer))
    }
    @Test fun `play after pause has an independent generation`() {
        val gate = PlaybackRequestGate()
        val older = gate.play()
        gate.cancel()
        val newer = gate.play()
        assertFalse(gate.isCurrent(older))
        assertTrue(gate.isCurrent(newer))
    }
}
