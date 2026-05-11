package gd.app.musicplayer.ui.library.albums

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.databinding.ActivityAlbumBinding
import gd.app.musicplayer.ui.library.folder.FolderFragment
import gd.app.musicplayer.ui.playlist.PlaylistFragment
import gd.app.musicplayer.core.common.extension.parcelable
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.ui.common.base.BasePlayerSheetActivity
import gd.app.musicplayer.ui.library.ARG_MUSIC_SET
import gd.app.musicplayer.ui.library.LibraryFragment
import gd.app.musicplayer.ui.library.musicset.MusicSetListFragment

@AndroidEntryPoint
class AlbumActivity : BasePlayerSheetActivity() {

    private lateinit var binding: ActivityAlbumBinding

    override val playerSheet: FrameLayout
        get() = binding.playerSheet

    override val miniPlayer: View
        get() = binding.miniPlayer

    override val fullPlayer: View
        get() = binding.bottomPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAlbumBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupPlayerSheet()

        if (savedInstanceState == null) {
            val fragment = readMusicSetFromIntent()?.let { musicSet ->
                when (musicSet) {
                    is MusicSet.Artists, is MusicSet.Albums, is MusicSet.Genres -> MusicSetListFragment.Companion.newInstance(musicSet)
                    is MusicSet.Folders -> FolderFragment.newInstance()
                    is MusicSet.Playlists -> PlaylistFragment.newInstance()
                    else -> null
                }
            } ?: LibraryFragment()

            supportFragmentManager.beginTransaction()
                .replace(binding.mainFragmentContainer.id, fragment)
                .commitNow()
        }
    }

    private fun readMusicSetFromIntent(): MusicSet? {
        return intent.parcelable(ARG_MUSIC_SET)
    }

    companion object {
        fun start(context: Context, musicSet: MusicSet? = null) {
            context.startActivityCompat(Intent(context, AlbumActivity::class.java).apply {
                musicSet?.let { putExtra(ARG_MUSIC_SET, it) }
            })
        }
    }
}
