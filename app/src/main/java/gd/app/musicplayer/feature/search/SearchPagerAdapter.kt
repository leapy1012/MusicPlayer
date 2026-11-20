package gd.app.musicplayer.feature.search

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

/** Original `l5.t1.c`: one fragment per search tab. */
class SearchPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    private val pages = SearchPage.pages

    override fun getItemCount(): Int = pages.size

    override fun getItemId(position: Int): Long = pages[position].ordinal.toLong()

    override fun containsItem(itemId: Long): Boolean =
        pages.any { page -> page.ordinal.toLong() == itemId }

    override fun createFragment(position: Int): Fragment =
        SearchPageFragment.newInstance(pages[position])
}
