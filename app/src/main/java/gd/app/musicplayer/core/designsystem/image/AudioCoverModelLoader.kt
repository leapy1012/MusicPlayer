package gd.app.musicplayer.core.designsystem.image

import android.content.Context
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import java.io.InputStream

class AudioCoverModelLoader(
    private val context: Context,
) : ModelLoader<AudioCover, InputStream> {

    override fun buildLoadData(
        model: AudioCover,
        width: Int,
        height: Int,
        options: Options,
    ): ModelLoader.LoadData<InputStream> {
        return ModelLoader.LoadData(
            ObjectKey(model.source),
            AudioCoverFetcher(context.applicationContext, model.source),
        )
    }

    override fun handles(model: AudioCover): Boolean = model.source.isNotBlank()

    class Factory(
        private val context: Context,
    ) : ModelLoaderFactory<AudioCover, InputStream> {
        override fun build(
            multiFactory: MultiModelLoaderFactory,
        ): ModelLoader<AudioCover, InputStream> = AudioCoverModelLoader(context)

        override fun teardown() = Unit
    }
}
