package gd.app.musicplayer.feature.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.COUIRecyclerView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applySystemBarInsets
import gd.app.musicplayer.core.common.extension.navigateBack
import gd.app.musicplayer.databinding.FragmentLibraryBinding
import gd.app.musicplayer.domain.model.LibraryTabConfig
import gd.app.musicplayer.domain.model.LibraryTabConfigStore
import gd.app.musicplayer.feature.search.SearchActivity
import gd.app.musicplayer.ui.common.CouiTabLayoutMediator
import gd.app.musicplayer.ui.common.installEqualWidthTabs
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.ui.common.base.applyCouiLeftTitle
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LibraryFragment :
    ViewBindingFragment<FragmentLibraryBinding>(),
    Toolbar.OnMenuItemClickListener {

    private val viewModel: LibraryScreenViewModel by viewModels()

    private var visibleTabs: List<LibraryTabConfig> = emptyList()
    private var tabMediator: CouiTabLayoutMediator? = null
    private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null
    private var equalWidthListener: View.OnLayoutChangeListener? = null

    override fun onCreateBinding(inflater: LayoutInflater): FragmentLibraryBinding {
        return FragmentLibraryBinding.inflate(inflater)
    }

    override fun onBindingCreated(
        binding: FragmentLibraryBinding,
        savedInstanceState: Bundle?
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        binding.root.applySystemBarInsets(
            binding.statusBarSpace,
            binding.root
        )

        setupToolbar()
        observeUiState()
        setupBackPressHandler()
    }

    private fun setupToolbar() {
        val binding = requireBinding()

        binding.toolbar.applyCouiLeftTitle()
        binding.toolbar.navigateBack(requireActivity())
        binding.toolbar.menu.clear()
        binding.toolbar.inflateMenu(R.menu.menu_fragment_library)
        binding.toolbar.setOnMenuItemClickListener(this)
        // Keep app bar above the pager so menu taps are not stolen.
        binding.appBar.bringToFront()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::renderTabs)
            }
        }
    }

    private fun renderTabs(state: LibraryScreenUiState) {
        val binding = requireBinding()

        if (state.visibleTabs.isEmpty()) return

        if (
            visibleTabs == state.visibleTabs &&
            binding.viewPager.adapter != null
        ) {
            return
        }

        pageChangeCallback?.let { callback ->
            binding.viewPager.unregisterOnPageChangeCallback(callback)
        }

        tabMediator?.detach()

        visibleTabs = state.visibleTabs

        binding.viewPager.adapter = null
        binding.viewPager.offscreenPageLimit = 1
        binding.viewPager.isUserInputEnabled = true
        binding.viewPager.adapter = LibraryPagerAdapter(
            fragment = this,
            items = visibleTabs
        )

        tabMediator = CouiTabLayoutMediator(
            tabLayout = binding.tabLayout,
            viewPager = binding.viewPager
        ) { tab, position ->
            tab.text = getString(
                LibraryTabConfigStore.labelRes(visibleTabs[position].id)
            )
        }.also { mediator ->
            mediator.attach()
        }

        equalWidthListener?.let(binding.tabLayout::removeOnLayoutChangeListener)
        equalWidthListener = binding.tabLayout.installEqualWidthTabs()

        val callback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                visibleTabs.getOrNull(position)
                    ?.id
                    ?.let(viewModel::onTabSelected)
                bindAppBarToCurrentList()
            }
        }

        pageChangeCallback = callback
        binding.viewPager.registerOnPageChangeCallback(callback)

        binding.viewPager.setCurrentItem(
            state.initialTabIndex.coerceIn(0, visibleTabs.lastIndex),
            false
        )
        binding.viewPager.post { bindAppBarToCurrentList() }
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

    private fun findCurrentRecyclerView(): RecyclerView? {
        val fragment = getCurrentLibraryChildFragment() ?: return null
        return fragment.view?.findViewById(R.id.recyclerview)
    }

    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    saveCurrentTab()
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        )
    }

    private fun saveCurrentTab() {
        val binding = binding ?: return
        val tabId = visibleTabs.getOrNull(binding.viewPager.currentItem)?.id ?: return
        viewModel.onTabSelected(tabId)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_search -> {
                SearchActivity.start(requireContext())
                true
            }

            R.id.menu_more -> {
                val toolbar = requireBinding().toolbar
                val anchor = toolbar.findViewById<View>(R.id.menu_more) ?: toolbar
                (getCurrentLibraryChildFragment() as? ListMoreMenuHost)
                    ?.showMoreMenu(anchor)
                true
            }

            else -> false
        }
    }

    private fun getCurrentLibraryChildFragment(): Fragment? {
        val binding = requireBinding()
        val adapter = binding.viewPager.adapter ?: return null
        val tag = "f${adapter.getItemId(binding.viewPager.currentItem)}"
        return childFragmentManager.findFragmentByTag(tag)
    }

    override fun onDestroyView() {
        val currentBinding = binding

        if (currentBinding != null) {
            equalWidthListener?.let {
                currentBinding.tabLayout.removeOnLayoutChangeListener(it)
            }
            pageChangeCallback?.let { callback ->
                currentBinding.viewPager.unregisterOnPageChangeCallback(callback)
            }
            currentBinding.appBar.bindRecyclerView(null)
            currentBinding.viewPager.adapter = null
        }

        equalWidthListener = null
        pageChangeCallback = null
        tabMediator?.detach()
        tabMediator = null
        visibleTabs = emptyList()

        super.onDestroyView()
    }
}
