package gd.app.musicplayer.domain.repository

import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.model.SmartPlaylistConfig
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory last-good library lists so reopening Library paints immediately
 * (original felt fast because SQLite was already warm + UI never blocked).
 */
@Singleton
class LibraryListSnapshotCache @Inject constructor() {

    private val tracksByKey = ConcurrentHashMap<String, List<Music>>()
    private val setsByKey = ConcurrentHashMap<String, List<MusicSet>>()

    @Volatile
    var sortStyleByKey: ConcurrentHashMap<String, String> = ConcurrentHashMap()

    @Volatile
    var sortDescendingByKey: ConcurrentHashMap<String, Boolean> = ConcurrentHashMap()

    @Volatile
    var smartPlaylistConfig: SmartPlaylistConfig? = null

    fun tracksKey(musicSet: MusicSet, selectionMode: Boolean): String {
        return "tracks:${musicSet.id}:${musicSet.javaClass.simpleName}:$selectionMode"
    }

    fun setsKey(type: MusicSet): String {
        return "sets:${type.javaClass.simpleName}:${type.id}"
    }

    fun getTracks(key: String): List<Music>? = tracksByKey[key]

    fun putTracks(key: String, tracks: List<Music>) {
        tracksByKey[key] = tracks
    }

    fun getSets(key: String): List<MusicSet>? = setsByKey[key]

    fun putSets(key: String, sets: List<MusicSet>) {
        setsByKey[key] = sets
    }

    fun rememberSort(key: String, style: String, descending: Boolean) {
        sortStyleByKey[key] = style
        sortDescendingByKey[key] = descending
    }
}
