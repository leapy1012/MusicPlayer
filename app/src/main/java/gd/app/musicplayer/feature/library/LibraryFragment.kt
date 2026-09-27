package gd.app.musicplayer.feature.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
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
import com.coui.appcompat.tablayout.COUITabLayout
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applySystemBarInsets
import gd.app.musicplayer.core.common.extension.navigateBack
import gd.app.musicplayer.databinding.FragmentLibraryBinding
import gd.app.musicplayer.domain.model.LibraryTabConfig
import gd.app.musicplayer.domain.model.LibraryTabConfigStore
import gd.app.musicplayer.feature.search.SearchActivity
import gd.app.musicplayer.ui.common.CouiTabLayoutMediator
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

        installEqualWidthTabs(binding.tabLayout)

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

    private fun installEqualWidthTabs(tabLayout: COUITabLayout) {
        equalWidthListener?.let { tabLayout.removeOnLayoutChangeListener(it) }
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            applyEqualWidthTabs(tabLayout)
        }
        equalWidthListener = listener
        tabLayout.addOnLayoutChangeListener(listener)
        tabLayout.post { applyEqualWidthTabs(tabLayout) }
    }

    private fun applyEqualWidthTabs(tabLayout: COUITabLayout) {
        val strip = tabLayout.getChildAt(0) as? ViewGroup ?: return
        val count = strip.childCount
        val totalWidth = tabLayout.width
        if (count <= 0 || totalWidth <= 0) return

        // COUI FIXED mode does not distribute equal widths. When content is
        // shorter than the strip, measureShortChild centers wrap-content tabs.
        // Force each tab's minimumWidth to fill equally so measure keeps them
        // edge-to-edge (layoutParams.width alone is overwritten on remeasure).
        val baseWidth = totalWidth / count
        val remainder = totalWidth % count
        tabLayout.setRequestedTabMaxWidth(baseWidth + if (remainder > 0) 1 else 0)
        tabLayout.setPadding(0, tabLayout.paddingTop, 0, tabLayout.paddingBottom)
        // Indicator tracks TextView bounds (not tab bounds); ratio attr is unused
        // in this COUI strip. Stretch the label so the underline spans the tab.
        tabLayout.setIndicatorWidthRatio(1f)

        var changed = false
        for (index in 0 until count) {
            val child = strip.getChildAt(index)
            val tabWidth = baseWidth + if (index < remainder) 1 else 0
            if (child.minimumWidth != tabWidth) {
                child.minimumWidth = tabWidth
                changed = true
            }
            val params = child.layoutParams as LinearLayout.LayoutParams
            if (params.weight != 0f ||
                params.marginStart != 0 ||
                params.marginEnd != 0 ||
                params.leftMargin != 0 ||
                params.rightMargin != 0
            ) {
                params.weight = 0f
                params.marginStart = 0
                params.marginEnd = 0
                params.leftMargin = 0
                params.rightMargin = 0
                child.layoutParams = params
                changed = true
            }
            if (child.paddingStart != 0 || child.paddingEnd != 0) {
                child.setPadding(0, child.paddingTop, 0, child.paddingBottom)
                changed = true
            }
            if (stretchTabLabel(child, tabWidth)) {
                changed = true
            }
        }
        if (changed) {
            strip.requestLayout()
            tabLayout.post { tabLayout.tabStrip?.updateIndicatorPosition() }
        }
    }

    private fun stretchTabLabel(tabView: View, tabWidth: Int): Boolean {
        val label = (tabView as? com.coui.appcompat.tablayout.COUITabView)?.textView
            ?: return false
        var changed = false
        val params = label.layoutParams
        if (params != null && params.width != ViewGroup.LayoutParams.MATCH_PARENT) {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            label.layoutParams = params
            changed = true
        }
        if (label.minimumWidth != tabWidth) {
            label.minimumWidth = tabWidth
            changed = true
        }
        if (label is android.widget.TextView && label.gravity != android.view.Gravity.CENTER) {
            label.gravity = android.view.Gravity.CENTER
            changed = true
        }
        return changed
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
