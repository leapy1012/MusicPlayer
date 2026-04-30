package gd.app.musicplayer.playback.queue

fun interface QueueItemMatcher<T> {
    fun areSame(left: T?, right: T?): Boolean
}
