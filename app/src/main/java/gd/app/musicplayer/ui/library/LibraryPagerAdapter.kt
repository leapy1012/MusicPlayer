package gd.app.musicplayer.ui.library

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.ui.library.model.LibraryTabConfig
import gd.app.musicplayer.ui.library.musicset.MusicSetListFragment
import gd.app.musicplayer.ui.library.tracks.TrackListFragment

class LibraryPagerAdapter(
    fragment: Fragment,
    private val items: List<LibraryTabConfig>
) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int {
        return items.size
    }

    override fun getItemId(position: Int): Long {
        return items[position].id.toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return items.any { item ->
            item.id.toLong() == itemId
        }
    }

    override fun createFragment(position: Int): Fragment {
        return when (items[position].id) {
            TAB_TRACKS -> TrackListFragment.newInstance(MusicSet.Tracks)
            TAB_ARTISTS -> MusicSetListFragment.newInstance(MusicSet.Artists)
            TAB_ALBUMS -> MusicSetListFragment.newInstance(MusicSet.Albums)
            TAB_GENRES -> MusicSetListFragment.newInstance(MusicSet.Genres)
            else -> TrackListFragment.newInstance(MusicSet.Tracks)
        }
    }

    private companion object {
        private const val TAB_TRACKS = 0
        private const val TAB_ARTISTS = 1
        private const val TAB_ALBUMS = 2
        private const val TAB_GENRES = 3
    }
}
