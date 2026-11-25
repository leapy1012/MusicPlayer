package gd.app.musicplayer.feature.library.directory

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.databinding.ActivityMusicDirectoryBinding
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.feature.library.options.MusicOptionsDialog
import gd.app.musicplayer.feature.player.full.MusicPlayActivity
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.RecyclerEmptyStateController
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import kotlinx.coroutines.launch

/**
 * Port of `com.ijoysoft.music.activity.ActivityMusicDirectory` (`MusicSet` id `-16`): a
 * storage-volume rooted directory browser over the scanned library.
 */
@AndroidEntryPoint
class MusicDirectoryActivity : BaseActivity() {

    private val viewModel: MusicDirectoryViewModel by viewModels()

    private lateinit var binding: ActivityMusicDirectoryBinding
    private lateinit var adapter: MusicDirectoryAdapter
    private lateinit var emptyStateController: RecyclerEmptyStateController
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutManager: LinearLayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMusicDirectoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupEmptyStateController()
        setupBackNavigation()

        observeUiState()
        observeEvents()
        observePlaybackHighlight()
    }

    private fun setupToolbar() {
        setupEdgeToEdgeToolbar(
            root = binding.root,
            statusBarView = binding.statusBarSpace,
            bottomPaddingView = binding.root,
            toolbar = binding.toolbar
        )
        // This screen owns a two-line header; leave COUI's built-in title views unused.
        binding.toolbar.title = null
        binding.toolbar.subtitle = null

        binding.appBar.bringToFront()
    }

    private fun setupRecyclerView() {
        adapter = MusicDirectoryAdapter(
            theme = themeEngine.currentTheme(),
            onEntryClick = { entry ->
                saveScrollState()
                viewModel.onEntryClicked(entry)
            },
            onTrackMenuClick = viewModel::onTrackMenuClicked,
            applyTheme = { view -> themeEngine.apply(view) }
        )

        layoutManager = LinearLayoutManager(this)

        recyclerView = binding.root.findViewById<RecyclerView>(R.id.recyclerview).apply {
            layoutManager = this@MusicDirectoryActivity.layoutManager
            adapter = this@MusicDirectoryActivity.adapter
            setHasFixedSize(true)
            itemAnimator = null
        }

        binding.appBar.bindRecyclerView(recyclerView)
    }

    private fun setupEmptyStateController() {
        emptyStateController = RecyclerEmptyStateController(
            recyclerView = recyclerView,
            emptyViewStub = findViewById(R.id.layout_list_empty)
        ).apply {
            setEmptyLottieAsset("folder_empty.json")
            setEmptyMessage(getString(R.string.music_empty))
            applyTheme(themeEngine.currentTheme())
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    saveScrollState()
                    if (viewModel.navigateUp()) return

                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        )
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handleEvent)
            }
        }
    }

    private fun observePlaybackHighlight() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.playbackHighlight.collect { state ->
                    adapter.submitPlaybackHighlight(state.currentMusicId, state.isPlaying)
                }
            }
        }
    }

    private fun render(state: MusicDirectoryUiState) {
        binding.directoryTitle.text = state.title
        binding.directoryPath.text = state.subtitle
        binding.directoryPath.isVisible = !state.subtitle.isNullOrBlank()

        adapter.submitEntries(state.entries)
        emptyStateController.setVisible(state.isEmpty)
    }

    private fun handleEvent(event: MusicDirectoryEvent) {
        when (event) {
            is MusicDirectoryEvent.RestoreScroll -> {
                recyclerView.post {
                    layoutManager.scrollToPositionWithOffset(event.position, event.offset)
                }
            }

            MusicDirectoryEvent.OpenNowPlaying -> MusicPlayActivity.start(this)

            is MusicDirectoryEvent.ShowTrackOptions -> {
                MusicOptionsDialog
                    .newInstance(event.music, MusicSet.Tracks)
                    .show(supportFragmentManager, MusicOptionsDialog::class.java.simpleName)
            }
        }
    }

    /** `ActivityMusicDirectory.p1`: remember first-visible row/offset for the node being left. */
    private fun saveScrollState() {
        val firstChild = layoutManager.getChildAt(0)
        val position = firstChild?.let(layoutManager::getPosition) ?: 0
        val offset = firstChild?.top ?: 0
        viewModel.saveScrollState(position, offset)
    }

    companion object {
        fun start(context: Context) {
            context.startActivityCompat(
                Intent(context, MusicDirectoryActivity::class.java)
            )
        }
    }
}
