package gd.app.musicplayer.feature.player.queue

import android.os.Bundle
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isGone
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fueled.draggablerecyclerview.DragItemTouchHelperCallback
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applyStatusBarInsetHeight
import gd.app.musicplayer.core.common.extension.dpToPx
import gd.app.musicplayer.core.common.extension.navigateBack
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.core.common.extension.isFavorite
import gd.app.musicplayer.core.common.extension.toDurationString
import gd.app.musicplayer.core.designsystem.theme.ThemePalette
import gd.app.musicplayer.core.designsystem.theme.accentColor
import gd.app.musicplayer.core.designsystem.theme.messageColor
import gd.app.musicplayer.core.designsystem.theme.titleColor
import gd.app.musicplayer.databinding.FragmentQueueBinding
import gd.app.musicplayer.databinding.MusicPlayFragmentListItemBinding
import gd.app.musicplayer.feature.library.options.QueueTrackOptionsDialog
import gd.app.musicplayer.feature.playlist.PlaylistSelectActivity
import gd.app.musicplayer.ui.selection.ItemMoveListener
import gd.app.musicplayer.core.designsystem.view.MusicRecyclerView
import gd.app.musicplayer.ui.common.base.RecyclerEmptyStateController
import gd.app.musicplayer.ui.common.base.QueueClearConfirmDialogFragment
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.ui.common.base.WrapContentLinearLayoutManager
import gd.app.musicplayer.ui.common.playback.PlayModeViewModel
import gd.app.musicplayer.feature.player.full.PlayerViewModel
import kotlinx.coroutines.launch
import java.util.Collections
import gd.app.musicplayer.domain.usecase.playlist.ToggleFavoriteTrackUseCase
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackQueueFragment : ViewBindingFragment<FragmentQueueBinding>(),
    Toolbar.OnMenuItemClickListener {

    @Inject
    lateinit var toggleFavoriteTrackUseCase: ToggleFavoriteTrackUseCase

    private val viewModel: PlayerViewModel by viewModels()
    private val queueViewModel: QueueViewModel by viewModels()
    private val playModeViewModel: PlayModeViewModel by viewModels()

    private lateinit var adapter: QueueListAdapter
    private lateinit var emptyStateController: RecyclerEmptyStateController
    private lateinit var recyclerView: MusicRecyclerView
    private lateinit var emptyViewStub: ViewStub
    private var currentQueue: List<Music> = emptyList()
    private var currentIndex: Int = -1
    private var localQueueOverride: List<Music>? = null
    private var initialScrollPending = true

    override fun onCreateBinding(inflater: LayoutInflater): FragmentQueueBinding =
        FragmentQueueBinding.inflate(inflater)

    override fun onBindingCreated(
        binding: FragmentQueueBinding,
        savedInstanceState: Bundle?
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        recyclerView = binding.root.findViewById(R.id.recyclerview)
        emptyViewStub = binding.root.findViewById(R.id.layout_list_empty)

        applyInsets(binding)
        setupDialogResults()
        setupToolbar(binding)
        adapter = buildAdapter()

        recyclerView.layoutManager =
            WrapContentLinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        recyclerView.adapter = adapter
        recyclerView.isNestedScrollingEnabled = true
        emptyStateController = RecyclerEmptyStateController(
            recyclerView = recyclerView,
            emptyViewStub = emptyViewStub
        ).apply {
            setEmptyMessage(getString(R.string.music_empty))
        }

        observePlayback()
        observePlayMode()
    }

    private fun applyInsets(binding: FragmentQueueBinding) {
        binding.statusBarSpace.applyStatusBarInsetHeight()
    }

    private fun setupToolbar(binding: FragmentQueueBinding) {
        binding.toolbar.navigateBack(this)
        binding.toolbar.inflateMenu(R.menu.menu_fragment_queue)
        binding.toolbar.setOnMenuItemClickListener(this)
        binding.queueClear.setOnClickListener {
            if (resolveQueue().isEmpty()) {
                ToastUtil.show(requireContext(), R.string.list_is_empty)
            } else {
                showClearQueueDialog()
            }
        }
    }

    private fun setupDialogResults() {
        parentFragmentManager.setFragmentResultListener(
            QueueClearConfirmDialogFragment.RESULT_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            if (bundle.getBoolean(QueueClearConfirmDialogFragment.RESULT_CONFIRMED)) {
                viewModel.clearQueue(requireContext())
            }
        }
    }

    private fun showClearQueueDialog() {
        if (parentFragmentManager.findFragmentByTag(QueueClearConfirmDialogFragment.TAG) != null) {
            return
        }

        QueueClearConfirmDialogFragment()
            .show(parentFragmentManager, QueueClearConfirmDialogFragment.TAG)
    }

    private fun buildAdapter(): QueueListAdapter {
        val theme = themeEngine.currentTheme()

        return QueueListAdapter(
            theme = theme,
            onTrackClicked = { position ->
                val queue = resolveQueue()
                if (position in queue.indices) {
                    viewModel.playQueue(requireContext(), queue, position)
                }
            },
            onToggleFavorite = { track ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val favorited = toggleFavoriteTrackUseCase(track.id)
                    adapter.updateFavorite(track.id, favorited)
                }
            },
            onTrackMoved = ::onTrackMovedLocally,
            onTrackMoveFinished = ::replaceQueuePreservingCurrentTrack,
            formatDuration = { durationMs -> durationMs.toLong().toDurationString() },
            onTrackMenu = { track ->
                QueueTrackOptionsDialog.newInstance(track)
                    .show(parentFragmentManager, QueueTrackOptionsDialog::class.java.simpleName)
            }
        )
    }

    private fun observePlayback() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                queueViewModel.queueState.collect { state ->
                    currentQueue = state.queue
                    currentIndex = state.currentIndex
                    val queue = resolveQueue()
                    val binding = requireBinding()
                    adapter.submitQueue(queue, currentIndex)
                    val isEmpty = queue.isEmpty()
                    binding.queueBannerLayout.isGone = isEmpty
                    emptyStateController.setVisible(isEmpty)
                    binding.collapsingToolbar.isTitleEnabled = isEmpty.not()
                    updateCollapsingHeight(isEmpty)
                    updateQueueInfo(queue)
                    if (!isEmpty && initialScrollPending && currentIndex in queue.indices) {
                        initialScrollPending = false
                        recyclerView.post {
                            (recyclerView.layoutManager as? LinearLayoutManager)
                                ?.scrollToPositionWithOffset(currentIndex, 0)
                        }
                    }
                }
            }
        }
    }

    private fun updateQueueInfo(queue: List<Music>) {
        val binding = requireBinding()
        val count = queue.size
        val current = if (count == 0) 0 else (currentIndex + 1).coerceIn(1, count)
        binding.queueInfo.text = "$current/$count"
    }

    private fun replaceQueuePreservingCurrentTrack(updatedQueue: List<Music>) {
        if (updatedQueue.isEmpty()) {
            viewModel.clearQueue(requireContext())
            return
        }
        val currentTrackId = currentQueue.getOrNull(currentIndex)?.id
        val nextIndex = updatedQueue.indexOfFirst { it.id == currentTrackId }
            .takeIf { it >= 0 }
            ?: currentIndex.coerceIn(0, updatedQueue.lastIndex)
        viewModel.replaceQueue(requireContext(), updatedQueue, nextIndex)
        localQueueOverride = null
    }

    private fun onTrackMovedLocally(updatedQueue: List<Music>) {
        localQueueOverride = updatedQueue
        updateQueueInfo(updatedQueue)
    }

    private fun updateCollapsingHeight(isEmpty: Boolean) {
        val binding = requireBinding()

        val baseHeight = resources.getDimensionPixelSize(R.dimen.common_title_height)
        val bannerHeight = requireContext().dpToPx(72f)
        val targetHeight = if (isEmpty) baseHeight else baseHeight + bannerHeight

        binding.collapsingToolbar.updateLayoutParams { height = targetHeight }
    }

    fun scrollToCurrentTrack() {
        val queue = resolveQueue()
        val index = currentIndex
        if (index !in queue.indices) {
            ToastUtil.show(requireContext(), R.string.no_music_enqueue)
            return
        }
        (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(index, 0)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_mode -> {
                playModeViewModel.cyclePlayMode()
            }

            R.id.menu_add_to -> {
                val queue = resolveQueue()
                if (queue.isEmpty()) {
                    ToastUtil.show(requireContext(), R.string.list_is_empty)
                } else {
                    PlaylistSelectActivity.start(requireContext(), queue)
                }
            }
        }
        return true
    }

    private fun resolveQueue(): List<Music> = localQueueOverride ?: currentQueue

    private fun observePlayMode() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                playModeViewModel.uiState.collect { state ->
                    requireBinding().toolbar.menu.findItem(R.id.menu_mode)?.setIcon(state.iconRes)
                }
            }
        }
    }
}

private class QueueListAdapter(
    private val theme: ThemePalette,
    private val onTrackClicked: (Int) -> Unit,
    private val onToggleFavorite: (Music) -> Unit,
    private val onTrackMoved: (List<Music>) -> Unit,
    private val onTrackMoveFinished: (List<Music>) -> Unit,
    private val formatDuration: (Int) -> String,
    private val onTrackMenu: (Music) -> Unit
) : RecyclerView.Adapter<QueueListAdapter.QueueViewHolder>(), ItemMoveListener {

    private val queue = mutableListOf<Music>()
    private val favoriteOverrides = mutableMapOf<Long, Boolean>()
    private var currentIndex = -1
    private lateinit var itemTouchHelper: ItemTouchHelper
    private var hasPendingReorder = false

    init {
        setHasStableIds(true)
    }

    fun submitQueue(items: List<Music>, currentIndex: Int) {
        queue.clear()
        queue.addAll(
            items.map { music ->
                val overriddenFavorite = favoriteOverrides[music.id] ?: return@map music
                music.copy(playlistId = if (overriddenFavorite) 1L else 0L)
            }
        )
        favoriteOverrides.keys.retainAll(queue.mapTo(hashSetOf()) { it.id })
        this.currentIndex = currentIndex
        notifyDataSetChanged()
    }

    fun updateFavorite(trackId: Long, favorited: Boolean) {
        val index = queue.indexOfFirst { it.id == trackId }
        if (index < 0) return
        favoriteOverrides[trackId] = favorited
        queue[index] = queue[index].copy(playlistId = if (favorited) 1L else 0L)
        notifyItemChanged(index)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        val callback = DragItemTouchHelperCallback.Builder(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        )
            .setDragEnabled(false)
            .onItemDragListener(::onItemMove)
            .onDragFinishedListener {
                if (!hasPendingReorder) return@onDragFinishedListener
                hasPendingReorder = false
                onTrackMoveFinished(queue.toList())
            }
            .build()
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    override fun getItemId(position: Int): Long = queue[position].id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val binding = MusicPlayFragmentListItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return QueueViewHolder(
            binding = binding,
            itemTouchHelper = itemTouchHelper,
            theme = theme
        )
    }

    override fun getItemCount(): Int = queue.size

    override fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition !in queue.indices || toPosition !in queue.indices) return
        Collections.swap(queue, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        onTrackMoved(queue.toList())
        hasPendingReorder = true
    }

    class QueueViewHolder(
        private val binding: MusicPlayFragmentListItemBinding,
        private val itemTouchHelper: ItemTouchHelper,
        private val theme: ThemePalette
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            music: Music,
            isCurrent: Boolean,
            formatDuration: (Int) -> String,
            onClick: () -> Unit,
            onFavoriteClick: () -> Unit,
            onMenuClick: () -> Unit
        ) {
            val accentColor = theme.accentColor
            val titleColor = if (isCurrent) accentColor else theme.titleColor
            val extraColor = if (isCurrent) accentColor else theme.messageColor

            binding.musicItemTitle.text = music.title
            binding.musicItemExtra.text = music.artist
            binding.musicItemTitle.setTextColor(titleColor)
            binding.musicItemExtra.setTextColor(extraColor)
            binding.musicItemTime.text = formatDuration(music.duration)

            binding.musicItemFavorite.visibility =
                if (isCurrent) View.VISIBLE else View.GONE

            binding.musicItemFavorite.isSelected = music.isFavorite()

            binding.musicItemFavorite.imageTintList = ColorStateList.valueOf(
                if (music.isFavorite()) {
                    accentColor
                } else {
                    theme.messageColor
                }
            )

            binding.root.alpha = 1f
            binding.root.setOnClickListener { onClick() }
            binding.musicItemMenu.setOnClickListener { onMenuClick() }
            binding.musicItemFavorite.setOnClickListener { onFavoriteClick() }

            binding.musicItemDrag.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    itemTouchHelper.startDrag(this)
                    return@setOnTouchListener true
                }

                false
            }
        }
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        holder.bind(
            music = queue[position],
            isCurrent = position == currentIndex,
            formatDuration = formatDuration,
            onClick = { onTrackClicked(position) },
            onFavoriteClick = { onToggleFavorite(queue[position]) },
            onMenuClick = { onTrackMenu(queue[position]) }
        )
    }
}



