package gd.app.musicplayer.core.designsystem.image

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.core.net.toUri
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.InputStream

class AudioCoverFetcher(
    private val context: Context,
    private val source: String,
) : DataFetcher<InputStream> {

    private var stream: InputStream? = null
    private var retriever: MediaMetadataRetriever? = null

    override fun loadData(
        priority: Priority,
        callback: DataFetcher.DataCallback<in InputStream>,
    ) {
        try {
            val mediaRetriever = MediaMetadataRetriever()
            retriever = mediaRetriever
            if (source.contains("://")) {
                mediaRetriever.setDataSource(context, source.toUri())
            } else {
                mediaRetriever.setDataSource(source)
            }
            val picture = mediaRetriever.embeddedPicture
            if (picture == null || picture.isEmpty()) {
                callback.onLoadFailed(FileNotFoundException("No embedded artwork: $source"))
                return
            }
            val input = ByteArrayInputStream(picture)
            stream = input
            callback.onDataReady(input)
        } catch (error: Exception) {
            callback.onLoadFailed(error)
        } finally {
            runCatching { retriever?.release() }
            retriever = null
        }
    }

    override fun cleanup() {
        runCatching { stream?.close() }
        stream = null
    }

    override fun cancel() = Unit

    override fun getDataClass(): Class<InputStream> = InputStream::class.java

    override fun getDataSource(): DataSource = DataSource.LOCAL
}
