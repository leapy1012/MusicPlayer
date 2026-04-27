package gd.app.musicplayer.feature.library

import android.content.Context
import android.content.Intent
import android.os.Bundle
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.databinding.ActivityAlbumBinding
import gd.app.musicplayer.ui.folder.FolderFragment
import gd.app.musicplayer.feature.playlist.PlaylistFragment
import gd.app.musicplayer.core.ui.extension.parcelable
import gd.app.musicplayer.core.ui.extension.startActivityCompat
import gd.app.musicplayer.ui.common.base.BaseActivity

@AndroidEntryPoint
class AlbumActivity : BaseActivity() {

    private lateinit var binding: ActivityAlbumBinding

//    override val playerSheetView: View
//        get() = binding.playerSheet
//
//    override val collapsedPlayerView: View
//        get() = binding.mainFragmentBanner
//
//    override val expandedPlayerView: View
//        get() = binding.mainFragmentBanner2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAlbumBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            val fragment = readMusicSetFromIntent()?.let { musicSet ->
                when (musicSet) {
                    is MusicSet.Artists, is MusicSet.Albums, is MusicSet.Genres -> MusicSetListFragment.newInstance(musicSet)
                    is MusicSet.Folders -> FolderFragment.newInstance()
                    is MusicSet.Playlists -> PlaylistFragment.Companion.newInstance()
                    else -> null
                }
            } ?: LibraryFragment()

            supportFragmentManager.beginTransaction()
                .replace(binding.mainFragmentContainer.id, fragment)
                .commitNow()
        }

//        setupPlayerSheet(savedInstanceState)
//        installCollapseOnBackPressed()
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
