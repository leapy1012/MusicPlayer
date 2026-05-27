package gd.app.musicplayer.playback.queue

import gd.app.musicplayer.domain.model.Music

data class QueueIdentity(
    val id: Long,
    val token: Int
)

fun Music.queueIdentity(): QueueIdentity = QueueIdentity(
    id = id,
    token = queueToken
)

fun Music.hasSameQueueIdentity(other: Music): Boolean {
    return id == other.id && queueToken == other.queueToken
}
