package gd.app.musicplayer.feature.library

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.util.LibraryTabConfig

class LibraryPagerAdapter(
    fragment: Fragment,
    private val items: List<LibraryTabConfig>
) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = items.size

    override fun createFragment(position: Int): Fragment {
        return when (items[position].id) {
            0 -> MusicListFragment.newInstance(MusicSet.Tracks)
            1 -> MusicSetListFragment.newInstance(MusicSet.Artists)
            2 -> MusicSetListFragment.newInstance(MusicSet.Albums)
            else -> MusicSetListFragment.newInstance(MusicSet.Genres)
        }
    }
}
