package gd.app.musicplayer.feature.library.deleted

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

    private val recyclerView: RecyclerView
        get() = binding.layoutRecyclerview.recyclerview

    private val mediaDeleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        handleSystemDeleteResult(result.resultCode)
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

    private fun setupRecyclerView() = with(recyclerView) {
        adapter = DeletedMusicAdapter(
            accentColor = themeRepo.getAccentColor(),
            onSelectionCountChanged = ::renderSelection
        ).also {
            this@DeletedMusicActivity.adapter = it
        }

        layoutManager = LinearLayoutManager(this@DeletedMusicActivity)
        adapter = this@DeletedMusicActivity.adapter
        setHasFixedSize(true)

        (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        emptyStateController = RecyclerEmptyStateController(
            recyclerView = this,
            emptyViewStub = binding.layoutRecyclerview.layoutListEmpty
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
            val confirmed = bundle.getBoolean(
                DeletedMusicDeleteConfirmDialogFragment.RESULT_CONFIRMED
            )

            if (confirmed) {
                deletePendingSourceFiles()
            }
        }
    }

    private fun observeTracks() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.tracks.collect(::renderTracks)
            }
        }
    }

    private fun renderTracks(tracks: List<Music>) {
        adapter.submitList(tracks)
        emptyStateController.setVisible(tracks.isEmpty())
        renderSelection(adapter.getSelectedItems().size)
    }

    private fun toggleSelectAll() {
        if (adapter.itemCount == 0) return

        adapter.setAllSelected(
            selected = !adapter.areAllItemsSelected()
        )
    }

    private fun renderSelection(selectedCount: Int) {
        selectAllView.renderSelectAllState(
            SelectionUiState(
                selectedCount = selectedCount,
                selectableCount = adapter.itemCount
            )
        )

        binding.toolbar.title = musicSelectionTitle(
            selectedCount = selectedCount,
            emptyTitleRes = R.string.batch_edit
        )
    }

    private fun restoreSelected() {
        val selected = selectedOrShowEmptyToast() ?: return

        lifecycleScope.launch {
            viewModel.restore(selected)
            onSelectionActionSucceeded()
        }
    }

    private fun confirmDeleteSourceFiles() {
        pendingDeleteSourceFiles = selectedOrShowEmptyToast() ?: return

        DeletedMusicDeleteConfirmDialogFragment().show(
            supportFragmentManager,
            DeletedMusicDeleteConfirmDialogFragment::class.java.simpleName
        )
    }

    private fun deletePendingSourceFiles() {
        val selected = pendingDeleteSourceFiles
        if (selected.isEmpty()) return

        lifecycleScope.launch {
            val deletedCount = viewModel.deleteSourceFiles(selected)
            handleDirectDeleteResult(
                selected = selected,
                deletedCount = deletedCount
            )
        }
    }

    private fun handleDirectDeleteResult(
        selected: List<Music>,
        deletedCount: Int
    ) {
        val expectedCount = selected.distinctBy(Music::id).size

        when {
            deletedCount == expectedCount -> {
                clearPendingDelete()
                onSelectionActionSucceeded()
            }

            requestSystemMediaDelete(selected) -> {
                // Wait for system picker result.
            }

            deletedCount > 0 -> {
                clearPendingDelete()
                onSelectionActionSucceeded()
            }

            else -> {
                clearPendingDelete()
                ToastUtil.show(this, R.string.feature_not_implemented)
            }
        }
    }

    private fun handleSystemDeleteResult(resultCode: Int) {
        val tracks = pendingDeleteSourceFiles
        clearPendingDelete()

        lifecycleScope.launch {
            if (resultCode == RESULT_OK && tracks.isNotEmpty()) {
                viewModel.markDeletedSourceFilesRemoved(tracks)
                onSelectionActionSucceeded()
            } else {
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
            .filter { track -> track.id > 0L }
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

        mediaDeleteLauncher.launch(
            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
        )

        return true
    }

    private fun selectedOrShowEmptyToast(): List<Music>? {
        val selected = adapter.getSelectedItems()

        if (selected.isNotEmpty()) {
            return selected
        }

        ToastUtil.show(this, R.string.select_musics_empty)
        return null
    }

    private fun onSelectionActionSucceeded() {
        ToastUtil.show(this, R.string.succeed)
        adapter.clearSelection()
        renderSelection(selectedCount = 0)
    }

    private fun clearPendingDelete() {
        pendingDeleteSourceFiles = emptyList()
    }

    companion object {
        fun start(context: Context) {
            context.startActivityCompat(
                Intent(context, DeletedMusicActivity::class.java)
            )
        }
    }
}