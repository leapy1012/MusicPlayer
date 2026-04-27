package gd.app.musicplayer.playback

object EqualizerPresetCatalog {

    val fiveBandPresets: List<IntArray> = listOf(
        intArrayOf(0, 0, 0, 0, 0),
        intArrayOf(200, 0, 0, 0, 200),
        intArrayOf(500, 200, -200, 400, 400),
        intArrayOf(600, 0, 200, 400, 100),
        intArrayOf(0, 0, 0, 0, 0),
        intArrayOf(200, 0, 0, 200, -100),
        intArrayOf(400, 100, 900, 200, 0),
        intArrayOf(500, 200, 0, 100, 200),
        intArrayOf(400, 200, -200, 200, 500),
        intArrayOf(-100, 200, 500, 100, -200),
        intArrayOf(500, 200, -100, 200, 500),
        intArrayOf(500, 200, 200, 400, 400),
        intArrayOf(600, 400, 100, 0, 0),
        intArrayOf(0, 0, 100, 400, 600),
        intArrayOf(-300, -200, 200, 280, -100),
        intArrayOf(600, 500, 0, 200, 50),
        intArrayOf(500, 50, 350, 0, -480),
        intArrayOf(500, 30, 0, 30, 500),
        intArrayOf(350, 0, -100, -50, 200),
        intArrayOf(550, 0, -120, -400, 200),
        intArrayOf(-300, 0, 200, -100, 100),
        intArrayOf(200, 200, 250, 420, 410),
        intArrayOf(520, 200, -300, 250, 400)
    )

    val tenBandPresets: List<IntArray> = listOf(
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(200, 0, 0, 0, 0, 0, 0, 0, -100, 100),
        intArrayOf(0, 0, 0, 0, 0, 0, -600, -600, -600, -700),
        intArrayOf(800, 600, 200, 0, 0, -400, -600, -600, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 200, 0, 0, 100, 400, 500, 600, 0, 0),
        intArrayOf(-200, 500, 400, -200, -200, -100, 200, 200, 100, 400),
        intArrayOf(600, 500, 800, 200, 0, 0, 0, 0, 0, 0),
        intArrayOf(200, 200, 100, 200, -100, -100, 0, 100, 200, 400),
        intArrayOf(-100, 0, 0, 100, 400, 200, 100, 0, -100, 100),
        intArrayOf(500, 200, -300, -600, -300, 200, 600, 800, 800, 800),
        intArrayOf(400, 400, 200, 200, 0, 100, 200, 200, 100, 200),
        intArrayOf(600, 400, 600, 200, 0, 0, 0, 0, 0, 0),
        intArrayOf(900, 900, 900, 500, 0, 400, 1100, 1100, 1100, 1100),
        intArrayOf(1000, 1000, 500, -500, -300, 200, 800, 1000, 1100, 1200),
        intArrayOf(200, 200, 0, 0, 200, 200, 400, 500, 0, 0),
        intArrayOf(0, 0, 200, 600, 600, 600, 200, 0, 0, 0),
        intArrayOf(400, 400, 200, 200, -100, -100, 0, 100, 200, 400),
        intArrayOf(400, 200, -400, -600, 0, 0, 200, 400, 400, 500),
        intArrayOf(-400, 0, 500, 600, 700, 600, 200, 200, 100, 0),
        intArrayOf(200, 0, -200, -400, -200, 200, 500, 700, 800, 900),
        intArrayOf(0, 200, 0, 0, 100, 400, 500, 200, 0, 100),
        intArrayOf(200, 600, 400, 0, -200, -100, 200, 200, 100, 200)
    )
}
