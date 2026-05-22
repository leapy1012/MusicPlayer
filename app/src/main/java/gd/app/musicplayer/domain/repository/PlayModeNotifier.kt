package gd.app.musicplayer.domain.repository

interface PlayModeNotifier {
    fun notifyModeChanged(mode: Int)
}
