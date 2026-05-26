package gd.app.musicplayer.playback

import gd.app.musicplayer.domain.model.Music
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackCommandPayloadStore @Inject constructor() {

    private val queuePayloads = ConcurrentHashMap<String, List<Music>>()

    fun putQueue(queue: List<Music>): String {
        val token = UUID.randomUUID().toString()
        queuePayloads[token] = queue.toList()
        return token
    }

    fun consumeQueue(token: String?): List<Music> {
        if (token.isNullOrBlank()) return emptyList()
        return queuePayloads.remove(token).orEmpty()
    }
}

