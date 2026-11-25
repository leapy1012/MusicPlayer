package gd.app.musicplayer.feature.search

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.COUIRecyclerView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.coui.appcompat.searchview.COUISearchBar
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applySystemBarInsets
import gd.app.musicplayer.core.common.extension.hideKeyboard
import gd.app.musicplayer.core.common.extension.showKeyboardDelayed
import gd.app.musicplayer.databinding.FragmentSearchBinding
import gd.app.musicplayer.feature.player.full.MusicPlayActivity
import gd.app.musicplayer.ui.common.CouiTabLayoutMediator
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.ui.common.enableTapToEdit
import kotlinx.coroutines.launch

/**
 * Port of `l5.t1`: search bar plus the All/Songs/Albums/Artists/Folders/Playlists pager.
 *
 * The query lives in [SearchViewModel], which is scoped to this fragment so every page
 * collects the same result set (original `t1.g` fanning the query out to each child).
 */
@AndroidEntryPoint
class SearchFragment : ViewBindingFragment<FragmentSearchBinding>() {

    private val viewModel: SearchViewModel by viewModels()

    private var tabMediator: CouiTabLayoutMediator? = null
    private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null

    override fun onCreateBinding(inflater: LayoutInflater): FragmentSearchBinding =
        FragmentSearchBinding.inflate(inflater)

    override fun onBindingCreated(binding: FragmentSearchBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        setupToolbar()
        setupPager()
        observeEvents()
        viewModel.refreshSortOrder()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSortOrder()
    }

    private fun setupToolbar() {
        val binding = requireBinding()
        // Bottom pad only in 3-button nav; gesture mode stays edge-to-edge behind the indicator.
        binding.root.applySystemBarInsets(binding.statusBarSpace, binding.root)
        binding.appBar.bringToFront()

        val searchBar = binding.searchBar
        searchBar.setSearchAnimateType(COUISearchBar.TYPE_INSTANT_SEARCH)
        searchBar.changeStateImmediately(COUISearchBar.STATE_EDIT)
        searchBar.enableTapToEdit()
        // COUISearchBar's functional button is the standard Cancel text (coui_search_view_cancel).
        searchBar.functionalButton?.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        val editText = searchBar.searchEditText ?: return
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun afterTextChanged(s: Editable?) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setQuery(s?.toString().orEmpty())
            }
        })
        editText.setOnEditorActionListener { view, _, _ ->
            viewModel.setQuery(view.text?.toString().orEmpty())
            editText.hideKeyboard()
            true
        }
        editText.postDelayed({
            editText.requestFocus()
            editText.showKeyboardDelayed()
        }, 100L)
    }

    private fun setupPager() {
        val binding = requireBinding()

        binding.viewPager.adapter = SearchPagerAdapter(this)
        // Results are shared by the ViewModel; hidden pages do not need live row hierarchies.
        binding.viewPager.offscreenPageLimit = 1

        tabMediator = CouiTabLayoutMediator(
            tabLayout = binding.tabLayout,
            viewPager = binding.viewPager
        ) { tab, position ->
            tab.text = getString(SearchPage.at(position).titleRes)
        }.also { it.attach() }

        val callback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                (currentPageFragment() as? SearchPageFragment)?.renderLatestResults()
                scheduleBindAppBarToCurrentList()
            }
        }
        pageChangeCallback = callback
        binding.viewPager.registerOnPageChangeCallback(callback)

        scheduleBindAppBarToCurrentList()
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        SearchEvent.OpenNowPlaying -> MusicPlayActivity.start(requireContext())
                    }
                }
            }
        }
    }

    /** Original `t1.x`: "See all" on a section header selects that section's tab. */
    fun selectPage(page: SearchPage) {
        binding?.viewPager?.setCurrentItem(page.ordinal, true)
    }

    fun hideSearchKeyboard() {
        binding?.searchBar?.searchEditText?.hideKeyboard()
    }

    internal fun isSelectedPage(page: SearchPage): Boolean =
        binding?.viewPager?.currentItem == page.ordinal

    /**
     * The include-wrapped list of each page is created asynchronously by the pager, so the
     * app-bar divider is wired once the page is about to draw (same as [LibraryFragment]).
     */
    private fun scheduleBindAppBarToCurrentList() {
        val binding = binding ?: return
        binding.viewPager.post {
            val list = findCurrentRecyclerView()
            if (list != null) {
                list.doOnPreDraw { bindAppBarToCurrentList() }
            } else {
                binding.viewPager.post { bindAppBarToCurrentList() }
            }
        }
    }

    private fun bindAppBarToCurrentList() {
        val binding = binding ?: return
        val list = findCurrentRecyclerView() ?: return
        list.isNestedScrollingEnabled = true
        list.overScrollMode = View.OVER_SCROLL_ALWAYS
        if (list is COUIRecyclerView) {
            list.setOverScrollEnable(true)
        }
        binding.appBar.bindRecyclerView(list)
    }

    private fun findCurrentRecyclerView(): RecyclerView? =
        currentPageFragment()?.view?.findViewById(R.id.recyclerview)

    private fun currentPageFragment(): Fragment? {
        val binding = binding ?: return null
        val adapter = binding.viewPager.adapter ?: return null
        return childFragmentManager.findFragmentByTag(
            "f${adapter.getItemId(binding.viewPager.currentItem)}"
        )
    }

    override fun onDestroyBinding(binding: FragmentSearchBinding) {
        pageChangeCallback?.let(binding.viewPager::unregisterOnPageChangeCallback)
        pageChangeCallback = null
        tabMediator?.detach()
        tabMediator = null
        binding.appBar.bindRecyclerView(null)
        binding.viewPager.adapter = null
        super.onDestroyBinding(binding)
    }
}
