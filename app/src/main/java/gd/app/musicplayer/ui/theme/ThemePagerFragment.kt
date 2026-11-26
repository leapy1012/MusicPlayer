package gd.app.musicplayer.ui.theme

import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.core.designsystem.theme.ThemeManager
import gd.app.musicplayer.core.designsystem.theme.MusicAppearance
import gd.app.musicplayer.core.datastore.ThemeSettings
import gd.app.musicplayer.core.common.extension.isDarkTheme
import gd.app.musicplayer.databinding.FragmentThemePagerItemBinding
import gd.app.musicplayer.ui.common.base.SpacingItemDecoration
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ThemePagerFragment : ViewBindingFragment<FragmentThemePagerItemBinding>() {

    private val viewModel: ThemeViewModel by activityViewModels()

    private var tabIndex: Int = 0
    private lateinit var adapter: ThemeItemAdapter

    override fun onCreateBinding(inflater: LayoutInflater): FragmentThemePagerItemBinding =
        FragmentThemePagerItemBinding.inflate(inflater)

    override fun onBindingCreated(
        binding: FragmentThemePagerItemBinding,
        savedInstanceState: Bundle?
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        tabIndex = requireArguments().getInt(TAB_INDEX, 0)

        setupRecycler(binding)
        observeUiState()
    }

    private fun setupRecycler(binding: FragmentThemePagerItemBinding) {
        adapter = ThemeItemAdapter(
            tabIndex = tabIndex,
            onThemeSelected = { fileName ->
                viewModel.onThemeSelected(fileName, tabIndex)
            },
            onAddClicked = {
                (activity as? ThemeActivity)?.openCustomThemePicker()
            }
        )

        binding.themeRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = this@ThemePagerFragment.adapter
            isNestedScrollingEnabled = true

            if (itemDecorationCount == 0) {
                addItemDecoration(SpacingItemDecoration.all(paddingLeft))
            }
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderThemeCards(state)
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (view != null && ::adapter.isInitialized) renderThemeCards(viewModel.uiState.value)
    }

    private fun renderThemeCards(state: ThemeUiState) {
        val settings = state.settings ?: ThemeSettings()
        adapter.submitList(ThemeItemMapper.buildItems(
            themes = state.themes,
            selectedImageName = settings.imageName,
            customImageNames = settings.imageUris,
            selectedTabIndex = state.selectedTabIndex,
            tabIndex = tabIndex,
            themeType = MusicAppearance.themeType(settings, requireContext().isDarkTheme())
        ))
    }

    companion object {
        private const val TAB_INDEX = "tab_index"

        fun newInstance(tabIndex: Int): ThemePagerFragment {
            return ThemePagerFragment().apply {
                arguments = Bundle().apply {
                    putInt(TAB_INDEX, tabIndex)
                }
            }
        }
    }
}
