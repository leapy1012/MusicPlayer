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
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.databinding.FragmentSearchBinding
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.data.model.isConcreteCollection
import gd.app.musicplayer.core.ui.extension.appContainer
import gd.app.musicplayer.playback.MusicPlaybackController
import gd.app.musicplayer.feature.library.AlbumMusicActivity
import gd.app.musicplayer.feature.library.MusicOptionsDialog
import gd.app.musicplayer.feature.player.ActivityPlayQueue
import gd.app.musicplayer.ui.common.view.SearchView
import gd.app.musicplayer.core.ui.extension.applyStatusBarInsetHeight
import gd.app.musicplayer.core.ui.extension.navigateBack
import gd.app.musicplayer.util.PreferenceUtil
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchFragment : ViewBindingFragment<FragmentSearchBinding>(),
    SearchView.OnQueryTextListener,
    SearchResultAdapter.Listener {

    private lateinit var searchView: SearchView
    private lateinit var adapter: SearchResultAdapter
    private lateinit var recyclerView: RecyclerView
    private val viewModel: SearchViewModel by viewModels()

    override fun onCreateBinding(inflater: LayoutInflater): FragmentSearchBinding =
        FragmentSearchBinding.inflate(inflater)

    override fun onBindingCreated(binding: FragmentSearchBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        setupToolbar()
        setupList()
        observeSections()
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

        searchView = SearchView(requireContext()).apply {
            setOnQueryTextListener(this@SearchFragment)
            postDelayed({
                val editText: EditText = getEditText()
                editText.requestFocus()
//                o8.z.b(editText, f10611c)
            }, 100L)
        }
        binding.toolbar.addView(searchView, Toolbar.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT ))
    }

    private fun setupList() {
        val binding = requireBinding()
        adapter = SearchResultAdapter(requireContext()).also {
            it.setListener(this)
        }
        recyclerView = binding.root.findViewById(R.id.recyclerview)
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
        val preferences = PreferenceUtil.getInstance(requireContext())
        val playbackState = MusicPlaybackController.state.value
        if (preferences.isReplaySongEnabled() && playbackState.currentTrack?._id == song._id) {
            MusicPlaybackController.restartCurrentTrack(requireContext())
            if (preferences.isTrackClickOperationEnabled()) {
                ActivityPlayQueue.Companion.start(requireContext())
            }
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val (queue, startIndex) = viewModel.resolvePlaybackQueue(song)
            requireContext().appContainer.playTracksUseCase(
                requireContext(),
                queue,
                startIndex
            )
            if (preferences.isTrackClickOperationEnabled()) {
                ActivityPlayQueue.Companion.start(requireContext())
            }
        }
    }

    override fun onSongMenuClicked(song: Music) {
        MusicOptionsDialog.Companion.newInstance(song, MusicSet.Tracks)
            .show(parentFragmentManager, MusicOptionsDialog::class.java.simpleName)
    }

    override fun onMusicSetClicked(musicSet: MusicSet) {
        if (musicSet.isConcreteCollection) {
            AlbumMusicActivity.Companion.start(requireContext(), musicSet)
        }
    }
}
