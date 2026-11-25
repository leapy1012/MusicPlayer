package gd.app.musicplayer.playback.player

import android.graphics.Bitmap
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import gd.app.musicplayer.core.common.extension.toQueueMediaId
import gd.app.musicplayer.domain.model.Music
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Publishes artwork to the existing Media3 session player; never creates a session. */
@UnstableApi
class PlaybackSessionMetadata(
    private val scope: CoroutineScope,
    private val sessionPlayer: () -> QueueAwareSessionPlayer?,
    private val currentMusic: () -> Music?,
    private val queueSize: () -> Int,
    private val currentIndex: () -> Int,
    private val encodingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private var artworkJob: Job? = null
    private var generation = 0
    private var lastBitmap: Bitmap? = null
    private var lastBitmapGeneration = -1
    private var lastMediaId: String? = null
    private var lastArtwork: ByteArray? = null

    fun clear() {
        generation++
        artworkJob?.cancel()
        lastBitmap = null
        lastBitmapGeneration = -1
        lastMediaId = null
        lastArtwork = null
        sessionPlayer()?.publishMetadata(null, null)
    }

    fun updateMetadata(music: Music, artwork: Bitmap) {
        val mediaId = music.toQueueMediaId()
        if (currentMusic()?.toQueueMediaId() != mediaId) return
        // Bitmap pools can replace pixels while retaining the same object.
        val bitmapGeneration = if (artwork.isRecycled) -1 else artwork.generationId
        if (lastBitmap === artwork && lastBitmapGeneration == bitmapGeneration && lastMediaId == mediaId) {
            publish(music, lastArtwork)
            return
        }
        val request = ++generation
        artworkJob?.cancel()
        lastBitmap = artwork
        lastBitmapGeneration = bitmapGeneration
        lastMediaId = mediaId
        lastArtwork = null
        publish(music, null)
        if (artwork.isRecycled) return
        val ratio = minOf(1f, 512f / maxOf(artwork.width, artwork.height))
        val scaled = Bitmap.createScaledBitmap(artwork,
            (artwork.width * ratio).toInt().coerceAtLeast(1),
            (artwork.height * ratio).toInt().coerceAtLeast(1), true)
        // Glide may recycle its source while encoding runs; own this bounded copy.
        val owned = if (scaled === artwork) artwork.copy(Bitmap.Config.ARGB_8888, false) else scaled
        artworkJob = scope.launch {
            val bytes = withContext(encodingDispatcher) {
                try {
                    ByteArrayOutputStream().use { output ->
                        check(owned.compress(Bitmap.CompressFormat.PNG, 100, output))
                        output.toByteArray()
                    }
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) { null }
            } ?: return@launch
            if (request == generation && currentMusic()?.toQueueMediaId() == mediaId) {
                lastArtwork = bytes
                publish(currentMusic() ?: return@launch, bytes)
            }
        }
        artworkJob?.invokeOnCompletion { owned.recycle() }
    }

    private fun publish(music: Music, bytes: ByteArray?) {
        sessionPlayer()?.publishMetadata(music.toQueueMediaId(), MediaMetadata.Builder()
            .setTitle(music.title).setArtist(music.artist).setAlbumTitle(music.album)
            .setDurationMs(music.duration.toLong().coerceAtLeast(0))
            .setTrackNumber(currentIndex() + 1).setTotalTrackCount(queueSize())
            .setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER).build())
    }
}
