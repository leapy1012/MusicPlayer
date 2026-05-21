package gd.app.musicplayer.ui.library.deleted

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageView
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.databinding.ActivityDeletedMusicBinding
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.RecyclerEmptyStateController
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import gd.app.musicplayer.ui.selection.SelectionUiState
import gd.app.musicplayer.ui.selection.installSelectAllAction
import gd.app.musicplayer.ui.selection.musicSelectionTitle
import gd.app.musicplayer.ui.selection.renderSelectAllState
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DeletedMusicActivity : BaseActivity() {

    private val viewModel: DeletedMusicViewModel by viewModels()

    private lateinit var binding: ActivityDeletedMusicBinding
    private lateinit var adapter: DeletedMusicAdapter
    private lateinit var emptyStateController: RecyclerEmptyStateController
    private lateinit var selectAllView: ImageView
    private var pendingDeleteSourceFiles: List<Music> = emptyList()
    private var waitingForSystemDeleteResult = false

    private val mediaDeleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val tracks = pendingDeleteSourceFiles
        pendingDeleteSourceFiles = emptyList()
        waitingForSystemDeleteResult = false

        lifecycleScope.launch {
            if (result.resultCode == RESULT_OK && tracks.isNotEmpty()) {
                viewModel.markDeletedSourceFilesRemoved(tracks)
                ToastUtil.show(this@DeletedMusicActivity, R.string.succeed)
                adapter.clearSelection()
            } else {
                ToastUtil.show(this@DeletedMusicActivity, R.string.feature_not_implemented)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeletedMusicBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupActions()
        setupDialogResults()
        observeTracks()
    }

    private fun setupToolbar() {
        setupEdgeToEdgeToolbar(
            root = binding.root,
            statusBarView = binding.statusBarSpace,
            bottomPaddingView = binding.root,
            toolbar = binding.toolbar,
            titleRes = R.string.batch_edit
        )

        selectAllView = binding.toolbar.installSelectAllAction(layoutInflater) {
            toggleSelectAll()
        }
    }

    private fun setupRecyclerView() {
        adapter = DeletedMusicAdapter(
            accentColor = themeRepo.getAccentColor(),
            onSelectionCountChanged = ::renderSelection
        )

        val recyclerView = binding.root.findViewById<RecyclerView>(R.id.recyclerview)
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@DeletedMusicActivity)
            adapter = this@DeletedMusicActivity.adapter
            setHasFixedSize(true)
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        }

        emptyStateController = RecyclerEmptyStateController(
            recyclerView = recyclerView,
            emptyViewStub = binding.root.findViewById(R.id.layout_list_empty)
        ).apply {
            setEmptyMessage(getString(R.string.music_empty))
        }
    }

    private fun setupActions() {
        binding.restoreDeletedMusic.setOnClickListener {
            restoreSelected()
        }

        binding.deleteSourceFile.setOnClickListener {
            confirmDeleteSourceFiles()
        }
    }

    private fun setupDialogResults() {
        supportFragmentManager.setFragmentResultListener(
            DeletedMusicDeleteConfirmDialogFragment.RESULT_KEY,
            this
        ) { _, bundle ->
            if (bundle.getBoolean(DeletedMusicDeleteConfirmDialogFragment.RESULT_CONFIRMED)) {
                deletePendingSourceFiles()
            }
        }
    }

    private fun observeTracks() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.tracks.collect { tracks ->
                    adapter.submitList(tracks)
                    emptyStateController.setVisible(tracks.isEmpty())
                    renderSelection(adapter.getSelectedItems().size)
                }
            }
        }
    }

    private fun toggleSelectAll() {
        if (adapter.itemCount == 0) return
        adapter.setAllSelected(!adapter.areAllItemsSelected())
    }

    private fun renderSelection(selectedCount: Int) {
        selectAllView.renderSelectAllState(
            SelectionUiState(
                selectedCount = selectedCount,
                hasSelectableItems = adapter.itemCount > 0,
                allSelectableItemsSelected = adapter.areAllItemsSelected()
            )
        )

        binding.toolbar.title = musicSelectionTitle(
            selectedCount = selectedCount,
            emptyTitleRes = R.string.batch_edit
        )
    }

    private fun restoreSelected() {
        val selected = adapter.getSelectedItems()
        if (selected.isEmpty()) {
            ToastUtil.show(this, R.string.select_musics_empty)
            return
        }

        lifecycleScope.launch {
            viewModel.restore(selected)
            adapter.clearSelection()
            ToastUtil.show(this@DeletedMusicActivity, R.string.succeed)
        }
    }

    private fun confirmDeleteSourceFiles() {
        val selected = adapter.getSelectedItems()
        if (selected.isEmpty()) {
            ToastUtil.show(this, R.string.select_musics_empty)
            return
        }

        pendingDeleteSourceFiles = selected
        DeletedMusicDeleteConfirmDialogFragment()
            .show(
                supportFragmentManager,
                DeletedMusicDeleteConfirmDialogFragment::class.java.simpleName
            )
    }

    private fun deletePendingSourceFiles() {
        val selected = pendingDeleteSourceFiles
        if (selected.isEmpty()) return

        lifecycleScope.launch {
            val deletedCount = viewModel.deleteSourceFiles(selected)
            if (deletedCount == selected.distinctBy(Music::id).size) {
                pendingDeleteSourceFiles = emptyList()
                ToastUtil.show(this@DeletedMusicActivity, R.string.succeed)
                adapter.clearSelection()
            } else if (requestSystemMediaDelete(selected)) {
                return@launch
            } else if (deletedCount > 0) {
                pendingDeleteSourceFiles = emptyList()
                ToastUtil.show(this@DeletedMusicActivity, R.string.succeed)
                adapter.clearSelection()
            } else {
                pendingDeleteSourceFiles = emptyList()
                ToastUtil.show(this@DeletedMusicActivity, R.string.feature_not_implemented)
            }
        }
    }

    private fun requestSystemMediaDelete(tracks: List<Music>): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || tracks.isEmpty()) {
            return false
        }

        val uris = tracks
            .distinctBy(Music::id)
            .filter { it.id > 0L }
            .map { track ->
                ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    track.id
                )
            }

        if (uris.isEmpty()) return false

        val pendingIntent = runCatching {
            MediaStore.createDeleteRequest(contentResolver, uris)
        }.getOrNull() ?: return false

        waitingForSystemDeleteResult = true
        mediaDeleteLauncher.launch(
            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
        )
        return true
    }

    companion object {
        fun start(context: Context) {
            context.startActivityCompat(Intent(context, DeletedMusicActivity::class.java))
        }
    }
}
