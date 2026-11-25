package gd.app.musicplayer.feature.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.designsystem.theme.ThemePalette
import gd.app.musicplayer.databinding.LayoutRecyclerviewBinding
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.model.isConcreteCollection
import gd.app.musicplayer.domain.repository.ThemeRepo
import gd.app.musicplayer.feature.library.albums.AlbumMusicActivity
import gd.app.musicplayer.feature.library.musicset.MusicSetOptionsDialog
import gd.app.musicplayer.feature.library.options.MusicOptionsDialog
import gd.app.musicplayer.feature.player.full.PlayerViewModel
import gd.app.musicplayer.ui.common.base.RecyclerEmptyStateController
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * One search tab. Original `l5.w1` for [SearchPage.ALL], `l5.y1` for [SearchPage.SONGS] and
 * `l5.u1` for the remaining set types — all of them the same `i7.c` list, differing only in which
 * sections they receive and whether the section caps apply.
 */
@AndroidEntryPoint
class SearchPageFragment :
    ViewBindingFragment<LayoutRecyclerviewBinding>(),
    SearchResultAdapter.Listener {

    @Inject lateinit var themeRepo: ThemeRepo

    private val viewModel: SearchViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )
    private val playerViewModel: PlayerViewModel by activityViewModels()

    private val page: SearchPage by lazy {
        SearchPage.at(requireArguments().getInt(ARG_PAGE))
    }

    private val adapter by lazy {
        SearchResultAdapter(
            context = requireContext(),
            theme = themeRepo.getCorePalette(),
            combined = page.isCombined
        ).also { it.setListener(this) }
    }

    private lateinit var emptyStateController: RecyclerEmptyStateController

    override fun onCreateBinding(inflater: LayoutInflater): LayoutRecyclerviewBinding =
        LayoutRecyclerviewBinding.inflate(inflater)

    override fun onBindingCreated(
        binding: LayoutRecyclerviewBinding,
        savedInstanceState: Bundle?
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        setupList(binding)
        observeSections()
        observePlaybackHighlight()
        renderLatestResults()
    }

    override fun onResume() {
        super.onResume()
        renderLatestResults()
    }

    override fun onPause() {
        // Off-screen tabs must not keep drawing their empty-state animation.
        if (::emptyStateController.isInitialized) emptyStateController.finishAnimation()
        super.onPause()
    }

    private fun setupList(binding: LayoutRecyclerviewBinding) {
        val recyclerView = binding.recyclerview
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        recyclerView.itemAnimator = null
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    (parentFragment as? SearchFragment)?.hideSearchKeyboard()
                }
            }
        })

        emptyStateController = RecyclerEmptyStateController(
            recyclerView = recyclerView,
            emptyViewStub = binding.layoutListEmpty
        )
        applyPageTheme(themeRepo.getCorePalette())
        emptyStateController.setEmptyLottieAsset("music_empty.json")
        emptyStateController.setEmptyMessage(getString(R.string.no_search_results))
    }

    override fun onThemeChanged(palette: ThemePalette?) {
        super.onThemeChanged(palette)
        applyPageTheme(palette ?: themeRepo.getCorePalette())
    }

    private fun applyPageTheme(palette: ThemePalette) {
        val binding = binding ?: return
        SearchPageSurface.apply(binding.root, binding.recyclerview, palette)
        if (::emptyStateController.isInitialized) {
            emptyStateController.applyTheme(palette)
        }
    }

    private fun observeSections() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.results.collect { result ->
                    // Initial loading is not a confirmed empty search result.
                    if (result == null) return@collect
                    renderResults(result)
                }
            }
        }
    }

    internal fun renderLatestResults() {
        if (binding == null) return
        viewModel.results.value?.let(::renderResults)
    }

    private fun renderResults(result: SearchResults) {
        if ((parentFragment as? SearchFragment)?.isSelectedPage(page) != true) return
        val pageSections = sectionsForPage(result.sections)
        adapter.submitSections(pageSections, result.query)
        emptyStateController.setVisible(pageSections.isEmpty())
    }

    private fun sectionsForPage(
        sections: List<SearchResultAdapter.SearchSection>
    ): List<SearchResultAdapter.SearchSection> = sections
        .filter { page.isCombined || it.titleRes == page.titleRes }

    private fun observePlaybackHighlight() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                playerViewModel.playbackHighlightState.collect { state ->
                    adapter.submitPlaybackHighlight(state.currentMusicId, state.isPlaying)
                }
            }
        }
    }

    override fun onSongClicked(song: Music, preferredIndex: Int?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.onSongClicked(song, preferredIndex)
        }
    }

    override fun onSongMenuClicked(song: Music) {
        MusicOptionsDialog.newInstance(song, MusicSet.Tracks)
            .show(parentFragmentManager, MusicOptionsDialog::class.java.simpleName)
    }

    override fun onMusicSetClicked(musicSet: MusicSet) {
        if (musicSet.isConcreteCollection) {
            AlbumMusicActivity.start(requireContext(), musicSet)
        }
    }

    override fun onMusicSetMenuClicked(musicSet: MusicSet) {
        MusicSetOptionsDialog
            .newInstance(musicSet)
            .show(
                parentFragmentManager,
                MusicSetOptionsDialog::class.java.simpleName
            )
    }

    override fun onSeeAllClicked(sectionTitleRes: Int) {
        (parentFragment as? SearchFragment)
            ?.selectPage(SearchPage.forSectionTitle(sectionTitleRes))
    }

    override fun onDestroyBinding(binding: LayoutRecyclerviewBinding) {
        binding.recyclerview.adapter = null
        super.onDestroyBinding(binding)
    }

    companion object {
        private const val ARG_PAGE = "search_page"

        fun newInstance(page: SearchPage): SearchPageFragment =
            SearchPageFragment().apply {
                arguments = Bundle().apply { putInt(ARG_PAGE, page.ordinal) }
            }
    }
}
