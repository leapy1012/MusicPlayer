package gd.app.musicplayer.feature.theme

import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import dagger.hilt.android.AndroidEntryPoint
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
                    val items = ThemeItemMapper.buildItems(
                        themes = state.themes,
                        selectedImageName = state.settings?.imageName.orEmpty(),
                        customImageNames = state.settings?.customImageNames.orEmpty(),
                        selectedTabIndex = state.selectedTabIndex,
                        tabIndex = tabIndex
                    )
                    adapter.submitList(items)
                }
            }
        }
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
