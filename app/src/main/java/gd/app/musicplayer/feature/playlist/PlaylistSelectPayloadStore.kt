package gd.app.musicplayer.feature.playlist

import gd.app.musicplayer.domain.model.Music
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PlaylistSelectPayloadStore {

    private val payloads = ConcurrentHashMap<String, List<Music>>()

    fun putSongs(songs: List<Music>): String {
        val token = UUID.randomUUID().toString()
        payloads[token] = songs.toList()
        return token
    }

    fun consumeSongs(token: String?): List<Music> {
        if (token.isNullOrBlank()) return emptyList()
        return payloads.remove(token).orEmpty()
    }
}

