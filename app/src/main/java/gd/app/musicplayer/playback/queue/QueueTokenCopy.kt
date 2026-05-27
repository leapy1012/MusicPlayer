package gd.app.musicplayer.playback.queue

import gd.app.musicplayer.domain.model.Music

fun Music.copyWithQueueToken(source: Music): Music {
    queueToken = source.queueToken
    return this
}
