package gd.app.musicplayer.feature.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.repository.ThemeRepo
import gd.app.musicplayer.databinding.FragmentSearchBinding
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.domain.model.isConcreteCollection
import gd.app.musicplayer.core.common.extension.applyStatusBarInsetHeight
import gd.app.musicplayer.core.common.extension.applySystemBarInsets
import gd.app.musicplayer.core.common.extension.navigateBack
import gd.app.musicplayer.core.common.extension.showKeyboardDelayed
import gd.app.musicplayer.core.designsystem.view.SearchView
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

        val searchView = SearchView(requireContext()).apply {
            setOnQueryTextListener(this@SearchFragment)
            postDelayed({
                val editText: EditText = getEditText()
                editText.requestFocus()
                editText.showKeyboardDelayed()
            }, 100L)
        }
        binding.toolbar.addView(
            searchView,
            Toolbar.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun setupList() {
        val binding = requireBinding()
        val recyclerView: RecyclerView = binding.root.findViewById(R.id.recyclerview)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
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

