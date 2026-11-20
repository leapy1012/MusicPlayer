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
import androidx.recyclerview.widget.SimpleItemAnimator
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
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
    }

    private fun setupList(binding: LayoutRecyclerviewBinding) {
        val recyclerView = binding.recyclerview
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    (parentFragment as? SearchFragment)?.hideSearchKeyboard()
                }
            }
        })

        // Same gray plate as edit screens (BackgroundWithCard), not white CardBackground.
        val typed = requireContext().obtainStyledAttributes(
            intArrayOf(com.coui.appcompat.R.attr.couiColorBackgroundWithCard)
        )
        val listSurface = typed.getColor(0, 0xFFF0F1F2.toInt())
        typed.recycle()
        binding.root.setBackgroundColor(listSurface)
        recyclerView.setBackgroundColor(listSurface)

        emptyStateController = RecyclerEmptyStateController(
            recyclerView = recyclerView,
            emptyViewStub = binding.layoutListEmpty
        )
        emptyStateController.applyTheme(themeRepo.getCorePalette())
        emptyStateController.setEmptyMessage(getString(R.string.queue_search_result))
    }

    private fun observeSections() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sections.collect { sections ->
                    val pageSections = sectionsForPage(sections)
                    adapter.submitSections(pageSections, viewModel.currentQuery())
                    emptyStateController.setVisible(pageSections.isEmpty())
                }
            }
        }
    }

    /** Each page owns its section expansion state, so sections are copied rather than shared. */
    private fun sectionsForPage(
        sections: List<SearchResultAdapter.SearchSection>
    ): List<SearchResultAdapter.SearchSection> = sections
        .filter { page.isCombined || it.titleRes == page.titleRes }
        .map { it.copy() }

    private fun observePlaybackHighlight() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
