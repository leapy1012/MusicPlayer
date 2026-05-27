package gd.app.musicplayer.playback.command

import gd.app.musicplayer.domain.model.Music
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackCommandPayloadStore @Inject constructor() {

    private data class QueuePayload(
        val queue: List<Music>,
        val createdAtMs: Long
    )

    private val queuePayloads = ConcurrentHashMap<String, QueuePayload>()

    fun putQueue(queue: List<Music>): String {
        cleanupExpiredPayloads()
        val token = UUID.randomUUID().toString()
        queuePayloads[token] = QueuePayload(
            queue = queue.toList(),
            createdAtMs = System.currentTimeMillis()
        )
        return token
    }

    fun consumeQueue(token: String?): List<Music> {
        if (token.isNullOrBlank()) return emptyList()
        val payload = queuePayloads.remove(token) ?: return emptyList()
        return payload.queue
    }

    fun clear() {
        queuePayloads.clear()
    }

    private fun cleanupExpiredPayloads() {
        val now = System.currentTimeMillis()
        queuePayloads.entries.removeIf { entry ->
            now - entry.value.createdAtMs > PAYLOAD_TTL_MS
        }
    }

    private companion object {
        private const val PAYLOAD_TTL_MS = 2 * 60 * 1000L
    }
}
