package gd.app.musicplayer.core.designsystem.image

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule
import java.io.InputStream

@GlideModule
class MusicGlideModule : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.prepend(
            AudioCover::class.java,
            InputStream::class.java,
            AudioCoverModelLoader.Factory(context),
        )
    }

    override fun isManifestParsingEnabled(): Boolean = false
}
