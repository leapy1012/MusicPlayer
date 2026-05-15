package gd.app.musicplayer.playback.player

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import gd.app.musicplayer.domain.model.Music
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaItemMapper @Inject constructor() {

    fun toMediaItemOrNull(music: Music): MediaItem? {
        val mediaUri = music.resolveMediaUri() ?: return null

        return MediaItem.Builder()
            .setMediaId(music.id.toString())
            .setUri(mediaUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(music.title)
                    .setArtist(music.artist)
                    .setAlbumTitle(music.album)
                    .build()
            )
            .build()
    }

    fun toMediaItems(queue: List<Music>): List<MediaItem> {
        return queue.mapNotNull { music ->
            toMediaItemOrNull(music)
        }
    }

    private fun Music.resolveMediaUri(): Uri? {
        val source = data.orEmpty()

        if (source.isBlank()) return null

        return if (source.contains(URI_SCHEME_SEPARATOR)) {
            source.toUri()
        } else {
            Uri.fromFile(File(source))
        }
    }

    private companion object {
        const val URI_SCHEME_SEPARATOR = "://"
    }
}