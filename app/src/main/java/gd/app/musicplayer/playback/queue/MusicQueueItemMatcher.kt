package gd.app.musicplayer.playback.queue

import gd.app.musicplayer.data.model.Music

object MusicQueueItemMatcher : QueueItemMatcher<Music> {
    override fun areSame(left: Music?, right: Music?): Boolean {
        return left != null &&
            right != null &&
            left.id == right.id &&
            left.queueSourceType() == right.queueSourceType()
    }

    private fun Music.queueSourceType(): Long {
        return 0L
    }
}
