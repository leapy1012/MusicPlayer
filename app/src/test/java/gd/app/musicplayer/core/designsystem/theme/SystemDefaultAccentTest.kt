package gd.app.musicplayer.core.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemDefaultAccentTest {
    @Test fun systemSelectionIsUsedUntilUserChoosesAnAppAccent() {
        assertEquals(0xff006600.toInt(), SystemDefaultAccent.choose(false, 0xff990000.toInt(), 0xff006600.toInt()))
        assertEquals(0xff990000.toInt(), SystemDefaultAccent.choose(true, 0xff990000.toInt(), 0xff006600.toInt()))
    }
    @Test fun stockFrameworkKeepsTheOriginalAppAccent() {
        assertEquals(0xff990000.toInt(), SystemDefaultAccent.choose(false, 0xff990000.toInt(), null))
    }
}
