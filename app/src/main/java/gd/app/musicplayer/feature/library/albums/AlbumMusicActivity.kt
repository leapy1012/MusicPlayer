package gd.app.musicplayer.feature.library.albums

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.commit
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.core.common.extension.parcelable
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.databinding.ActivityAlbumMusicBinding
import gd.app.musicplayer.ui.common.base.BasePlayerSheetActivity
import gd.app.musicplayer.feature.library.ARG_MUSIC_SET

@AndroidEntryPoint
class AlbumMusicActivity : BasePlayerSheetActivity() {

    private lateinit var binding: ActivityAlbumMusicBinding

    override val playerSheet: FrameLayout
        get() = binding.playerSheet

    override val miniPlayer: View
        get() = binding.miniPlayer

    override val fullPlayer: View
        get() = binding.bottomPlayer

    override val playerSheetInsetTarget: View
        get() = binding.mainFragmentContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAlbumMusicBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPlayerSheet()

        val musicSet = getMusicSetOrFinish() ?: return

        if (savedInstanceState == null) {
            showAlbumMusicFragment(musicSet)
        }
    }

    private fun getMusicSetOrFinish(): MusicSet? {
        return readMusicSetFromIntent() ?: run {
            finish()
            null
        }
    }

    private fun readMusicSetFromIntent(): MusicSet? {
        return intent.parcelable(ARG_MUSIC_SET)
    }

    private fun showAlbumMusicFragment(musicSet: MusicSet) {
        supportFragmentManager.commit {
            replace(
                binding.mainFragmentContainer.id,
                AlbumMusicFragment.newInstance(musicSet)
            )
        }
    }

    companion object {

        fun start(
            context: Context,
            musicSet: MusicSet
        ) {
            val intent = Intent(context, AlbumMusicActivity::class.java).apply {
                putExtra(ARG_MUSIC_SET, musicSet)
            }

            context.startActivityCompat(intent)
        }
    }
}
