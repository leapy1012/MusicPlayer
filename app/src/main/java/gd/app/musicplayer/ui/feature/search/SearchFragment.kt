package gd.app.musicplayer.ui.feature.search

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
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.databinding.FragmentSearchBinding
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.data.model.isConcreteCollection
import gd.app.musicplayer.playback.PlaybackGateway
import gd.app.musicplayer.playback.queue.currentTrack
import gd.app.musicplayer.ui.feature.library.AlbumMusicActivity
import gd.app.musicplayer.ui.feature.library.MusicOptionsDialog
import gd.app.musicplayer.ui.feature.player.PlayQueueActivity
import gd.app.musicplayer.core.ui.view.SearchView
import gd.app.musicplayer.core.extension.applyStatusBarInsetHeight
import gd.app.musicplayer.core.extension.navigateBack
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchFragment : ViewBindingFragment<FragmentSearchBinding>(),
    SearchView.OnQueryTextListener,
    SearchResultAdapter.Listener {

    private val adapter by lazy {
        SearchResultAdapter(requireContext()).also { it.setListener(this) }
    }
    private val viewModel: SearchViewModel by viewModels()

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
        binding.statusBarSpace.applyStatusBarInsetHeight()
        binding.toolbar.navigateBack(this)

        val searchView = SearchView(requireContext()).apply {
            setOnQueryTextListener(this@SearchFragment)
            postDelayed({
                val editText: EditText = getEditText()
                editText.requestFocus()
//                o8.z.b(editText, f10611c)
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
    }

    private fun observeSections() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sections.collect(adapter::submitSections)
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        SearchEvent.OpenQueueScreen -> PlayQueueActivity.start(requireContext())
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

    override fun onSongClicked(song: Music) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.onSongClicked(song, PlaybackGateway.state.value.currentTrack?.id)
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
}

