package gd.app.musicplayer.feature.search

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.coui.appcompat.searchview.COUISearchBar
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.repository.ThemeRepo
import gd.app.musicplayer.databinding.FragmentSearchBinding
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.domain.model.isConcreteCollection
import gd.app.musicplayer.core.common.extension.applySystemBarInsets
import gd.app.musicplayer.core.common.extension.hideKeyboard
import gd.app.musicplayer.core.common.extension.navigateBack
import gd.app.musicplayer.core.common.extension.showKeyboardDelayed
import gd.app.musicplayer.core.designsystem.theme.ThemeManager
import gd.app.musicplayer.core.designsystem.view.SearchView
import gd.app.musicplayer.ui.common.enableTapToEdit
import gd.app.musicplayer.ui.common.base.RecyclerEmptyStateController
import gd.app.musicplayer.feature.library.albums.AlbumMusicActivity
import gd.app.musicplayer.feature.library.musicset.MusicSetOptionsDialog
import gd.app.musicplayer.feature.library.options.MusicOptionsDialog
import gd.app.musicplayer.feature.player.full.MusicPlayActivity
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchFragment : ViewBindingFragment<FragmentSearchBinding>(),
    SearchView.OnQueryTextListener,
    SearchResultAdapter.Listener {

    @Inject lateinit var themeRepo: ThemeRepo

    private val adapter by lazy {
        SearchResultAdapter(
            context = requireContext(),
            theme = themeRepo.getCorePalette()
        ).also { it.setListener(this) }
    }
    private val viewModel: SearchViewModel by viewModels()
    private lateinit var emptyStateController: RecyclerEmptyStateController

    override fun onCreateBinding(inflater: LayoutInflater): FragmentSearchBinding =
        FragmentSearchBinding.inflate(inflater)

    override fun onBindingCreated(binding: FragmentSearchBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        setupToolbar()
        setupList()
        observeSections()
        observeEvents()
        viewModel.refreshSortOrder()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSortOrder()
    }

    private fun setupToolbar() {
        val binding = requireBinding()
        binding.root.applySystemBarInsets(binding.statusBarSpace, binding.root)
        binding.toolbar.navigateBack(this)
        binding.appBar.bringToFront()

        val searchBar = binding.searchBar
        searchBar.setSearchAnimateType(COUISearchBar.TYPE_INSTANT_SEARCH)
        searchBar.changeStateImmediately(COUISearchBar.STATE_EDIT)
        searchBar.enableTapToEdit()
        val editText = searchBar.searchEditText ?: return
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun afterTextChanged(s: Editable?) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                onQueryTextChange(s?.toString().orEmpty())
            }
        })
        editText.setOnEditorActionListener { view, _, _ ->
            onQueryTextSubmit(view.text?.toString().orEmpty())
            editText.hideKeyboard()
            true
        }
        editText.postDelayed({
            editText.requestFocus()
            editText.showKeyboardDelayed()
        }, 100L)
    }

    private fun setupList() {
        val binding = requireBinding()
        val recyclerView: RecyclerView = binding.root.findViewById(R.id.recyclerview)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    binding.searchBar.searchEditText?.hideKeyboard()
                }
            }
        })
        if (themeRepo.getCorePalette().getThemeType() == ThemeManager.THEME_TYPE_LIGHT) {
            val typed = requireContext().obtainStyledAttributes(
                intArrayOf(com.coui.appcompat.R.attr.couiColorCardBackground)
            )
            recyclerView.setBackgroundColor(typed.getColor(0, Color.WHITE))
            typed.recycle()
        }
        emptyStateController = RecyclerEmptyStateController(
            recyclerView = recyclerView,
            emptyViewStub = binding.root.findViewById(R.id.layout_list_empty)
        )
        emptyStateController.applyTheme(themeRepo.getCorePalette())
        emptyStateController.setEmptyMessage(getString(R.string.queue_search_result))
    }

    private fun observeSections() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sections.collect { sections ->
                    adapter.submitSections(sections)
                    emptyStateController.setVisible(sections.isEmpty())
                }
            }
        }
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

    override fun onQueryTextChange(query: String): Boolean {
        viewModel.setQuery(query)
        adapter.setQuery(query)
        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        viewModel.setQuery(query)
        adapter.setQuery(query)
        return true
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
}

