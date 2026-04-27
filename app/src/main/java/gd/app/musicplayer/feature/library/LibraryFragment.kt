package gd.app.musicplayer.feature.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.ui.extension.applySystemBarInsets
import gd.app.musicplayer.core.ui.extension.navigateBack
import gd.app.musicplayer.databinding.FragmentLibraryBinding
import gd.app.musicplayer.databinding.LayoutLibraryTitleBinding
import gd.app.musicplayer.feature.search.SearchActivity
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.util.LibraryTabConfig
import gd.app.musicplayer.util.LibraryTabConfigStore
import kotlin.getValue

@AndroidEntryPoint
class LibraryFragment : ViewBindingFragment<FragmentLibraryBinding>(), Toolbar.OnMenuItemClickListener {
    private val viewModel: LibraryScreenViewModel by viewModels()
    private lateinit var backPressedCallback: OnBackPressedCallback
    private var visibleTabs: List<LibraryTabConfig> = emptyList()

    override fun onCreateBinding(inflater: LayoutInflater): FragmentLibraryBinding =
        FragmentLibraryBinding.inflate(inflater)

    override fun onBindingCreated(binding: FragmentLibraryBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        binding.root.applySystemBarInsets(binding.statusBarSpace, view)
        setupToolbar()
        setupTabs()
        setupBackPressHandler()
    }


    private fun setupToolbar() {

        val binding = requireBinding()
        binding.toolbar.inflateMenu(R.menu.menu_fragment_library)
        binding.toolbar.navigateBack(requireActivity())
        binding.toolbar.setOnMenuItemClickListener(this)

        val titleViewBinding = LayoutLibraryTitleBinding.inflate(layoutInflater, binding.toolbar, false)
        titleViewBinding.appwallTitle.text = getString(R.string.library).uppercase()
        binding.toolbar.addView(titleViewBinding.root)
    }

    private fun setupTabs()  {
        val binding = requireBinding()
        visibleTabs = viewModel.visibleLibraryTabs()
        val lastTab = viewModel.initialTabIndex(visibleTabs)

        binding.viewPager.adapter = LibraryPagerAdapter(this, visibleTabs)

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = getString(
                LibraryTabConfigStore.labelRes(visibleTabs[position].id)
            ).uppercase()
        }.attach()

        binding.viewPager.setCurrentItem(lastTab, false)
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                visibleTabs.getOrNull(position)?.id?.let(viewModel::onTabSelected)
            }
        })
    }

    private fun setupBackPressHandler() {
        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                saveCurrentTab()
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)
    }

    private fun saveCurrentTab() {
        val binding = binding ?: return
        val tabId = visibleTabs.getOrNull(binding.viewPager.currentItem)?.id ?: return
        viewModel.onTabSelected(tabId)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_search -> {
                SearchActivity.Companion.start(requireContext())
                true
            }
            R.id.menu_more -> {
                val anchor = requireBinding().toolbar.findViewById<View>(R.id.menu_more) ?: return true
                (getCurrentLibraryChildFragment() as? ListMoreMenuHost)?.showMoreMenu(anchor)
                true
            }

            else -> false
        }
    }

    private fun getCurrentLibraryChildFragment(): Fragment? {
        val binding = requireBinding()
        val tag = "f${binding.viewPager.adapter?.getItemId(binding.viewPager.currentItem)}"
        return childFragmentManager.findFragmentByTag(tag)
    }
}
