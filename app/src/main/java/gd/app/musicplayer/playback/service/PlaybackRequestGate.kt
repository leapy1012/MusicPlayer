package gd.app.musicplayer.playback.service

/** Main-thread request ordering, independent of asynchronous queue loading. */
internal class PlaybackRequestGate {
    private var generation = 0L
    var wantsPlayback = false
        private set
    fun play(): Long { wantsPlayback = true; return ++generation }
    fun cancel() { wantsPlayback = false; generation++ }
    fun token(): Long = generation
    fun isUnchanged(request: Long): Boolean = request == generation
    fun isCurrent(request: Long): Boolean = wantsPlayback && request == generation
}
