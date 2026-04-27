package gd.app.musicplayer.feature.library

import android.content.Context
import android.content.Intent
import android.os.Bundle
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.databinding.ActivityAlbumMusicBinding
import gd.app.musicplayer.core.ui.extension.parcelable
import gd.app.musicplayer.core.ui.extension.startActivityCompat
import gd.app.musicplayer.ui.common.base.BaseActivity

@AndroidEntryPoint
class AlbumMusicActivity : BaseActivity() {
    private lateinit var binding: ActivityAlbumMusicBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAlbumMusicBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val musicSet = readMusicSetFromIntent()

        if (musicSet == null) {
            finish()
            return
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(
                    binding.mainFragmentContainer.id,
                    AlbumMusicFragment.newInstance(musicSet)
                )
                .commitNow()
        }

    }

    

    private fun readMusicSetFromIntent(): MusicSet? {
        return intent.parcelable(ARG_MUSIC_SET)
    }

    companion object {
        fun start(context: Context, musicSet: MusicSet) {
            context.startActivityCompat(Intent(context, AlbumMusicActivity::class.java).apply {
                putExtra(ARG_MUSIC_SET, musicSet)
            })
        }
    }
}
