package gd.app.musicplayer.ui.feature.player

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
import gd.app.musicplayer.core.extension.appDependencies
import gd.app.musicplayer.core.extension.applyStatusBarInsetHeight
import gd.app.musicplayer.core.extension.dpToPx
import gd.app.musicplayer.core.extension.navigateBack
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.core.extension.isFavorite
import gd.app.musicplayer.core.theme.messageColor
import gd.app.musicplayer.core.theme.titleColor
import gd.app.musicplayer.databinding.FragmentQueueBinding
import gd.app.musicplayer.databinding.MusicPlayFragmentListItemBinding
import gd.app.musicplayer.ui.feature.library.QueueTrackOptionsDialog
import gd.app.musicplayer.ui.feature.playlist.ActivityPlaylistSelect
import gd.app.musicplayer.ui.feature.selection.ItemMoveListener
import gd.app.musicplayer.playback.PlaybackGateway
import gd.app.musicplayer.playback.queue.PlaybackState
import gd.app.musicplayer.playback.queue.currentTrack
import gd.app.musicplayer.core.ui.view.MusicRecyclerView
import gd.app.musicplayer.ui.common.base.RecyclerEmptyStateController
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.ui.common.base.WrapContentLinearLayoutManager
import gd.app.musicplayer.ui.common.playback.PlayModeViewModel
import gd.app.musicplayer.playback.PlaybackControlViewModel
import kotlinx.coroutines.launch
import java.util.Collections

@AndroidEntryPoint
class PlaybackQueueFragment : ViewBindingFragment<FragmentQueueBinding>(),
    Toolbar.OnMenuItemClickListener {

    private val viewModel: PlaybackControlViewModel by viewModels()
    private val playModeViewModel: PlayModeViewModel by viewModels()

    private lateinit var adapter: QueueListAdapter
    private lateinit var emptyStateController: RecyclerEmptyStateController
    private lateinit var recyclerView: MusicRecyclerView
    private lateinit var emptyViewStub: ViewStub
    private var playbackState = PlaybackGateway.state.value
    private var localQueueOverride: List<Music>? = null

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
        setupToolbar(binding)
        adapter = buildAdapter()

        recyclerView.layoutManager = WrapContentLinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
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
            if (playbackState.queue.isEmpty()) {
                ToastUtil.show(requireContext(), R.string.list_is_empty)
            } else {
                viewModel.clearQueue(requireContext())
            }
        }
    }

    private fun buildAdapter(): QueueListAdapter =
        QueueListAdapter(
            onTrackClicked = { position ->
                val queue = localQueueOverride ?: playbackState.queue
                if (position in queue.indices) {
                    viewModel.playQueue(requireContext(), queue, position)
                }
            },
            onToggleFavorite = { track ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val favorited = requireContext().appDependencies.toggleFavoriteTrackUseCase(track.id)
                    adapter.updateFavorite(track.id, favorited)
                }
            },
            onTrackMoved = ::onTrackMovedLocally,
            onTrackMoveFinished = ::replaceQueuePreservingCurrentTrack,
            onTrackMenu = { track ->
                QueueTrackOptionsDialog.newInstance(track)
                    .show(parentFragmentManager, QueueTrackOptionsDialog::class.java.simpleName)
            }
        )

    private fun observePlayback() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.playbackState.collect { state ->
                    playbackState = state
                    val queue = localQueueOverride ?: state.queue
                    val binding = requireBinding()
                    adapter.submitQueue(queue, state.currentIndex)
                    val isEmpty = queue.isEmpty()
                    binding.queueBannerLayout.isGone = isEmpty
                    emptyStateController.setVisible(queue.isEmpty())
                    binding.collapsingToolbar.isTitleEnabled = isEmpty.not()
                    updateCollapsingHeight(isEmpty)
                    updateQueueInfo(state, queue)
                }
            }
        }
    }

    private fun updateQueueInfo(state: PlaybackState, queue: List<Music>) {
        val binding = requireBinding()
        val count = queue.size
        val current = if (count == 0) 0 else (state.currentIndex + 1).coerceIn(1, count)
        binding.queueInfo.text = "$current/$count"
    }

    private fun replaceQueuePreservingCurrentTrack(updatedQueue: List<Music>) {
        if (updatedQueue.isEmpty()) {
            viewModel.clearQueue(requireContext())
            return
        }
        val currentTrackId = playbackState.currentTrack?.id
        val nextIndex = updatedQueue.indexOfFirst { it.id == currentTrackId }
            .takeIf { it >= 0 }
            ?: playbackState.currentIndex.coerceIn(0, updatedQueue.lastIndex)
        viewModel.replaceQueue(requireContext(), updatedQueue, nextIndex)
        localQueueOverride = null
    }

    private fun onTrackMovedLocally(updatedQueue: List<Music>) {
        localQueueOverride = updatedQueue
        updateQueueInfo(playbackState, updatedQueue)
    }

    private fun updateCollapsingHeight(isEmpty: Boolean) {
        val binding = requireBinding()

        val baseHeight = resources.getDimensionPixelSize(R.dimen.common_title_height)
        val bannerHeight = requireContext().dpToPx(72f)
        val targetHeight = if (isEmpty) baseHeight else baseHeight + bannerHeight

        binding.collapsingToolbar.updateLayoutParams { height = targetHeight }
    }

    fun scrollToCurrentTrack() {
        val index = playbackState.currentIndex
        if (index !in playbackState.queue.indices) {
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
                if (playbackState.queue.isEmpty()) {
                    ToastUtil.show(requireContext(), R.string.list_is_empty)
                } else {
                    ActivityPlaylistSelect.start(requireContext(), localQueueOverride ?: playbackState.queue)
                }
            }
        }
        return true
    }

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
    private val onTrackClicked: (Int) -> Unit,
    private val onToggleFavorite: (Music) -> Unit,
    private val onTrackMoved: (List<Music>) -> Unit,
    private val onTrackMoveFinished: (List<Music>) -> Unit,
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
        return QueueViewHolder(binding, itemTouchHelper)
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
        private val itemTouchHelper: ItemTouchHelper
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            music: Music,
            isCurrent: Boolean,
            onClick: () -> Unit,
            onFavoriteClick: () -> Unit,
            onMenuClick: () -> Unit
        ) {
            val context = binding.root.context
            val palette = context.appDependencies.themeRepo.getCorePalette(context)
            val accentColor = context.appDependencies.themeRepo.getAccentColor(context)
            val titleColor = if (isCurrent) accentColor else palette.titleColor
            val extraColor = if (isCurrent) accentColor else palette.messageColor

            binding.musicItemTitle.text = music.title
            binding.musicItemExtra.text = music.artist
            binding.musicItemTitle.setTextColor(titleColor)
            binding.musicItemExtra.setTextColor(extraColor)
            binding.musicItemTime.text = PlaybackGateway.formatTime(music.duration)
            binding.musicItemFavorite.visibility = if (isCurrent) View.VISIBLE else View.GONE
            binding.musicItemFavorite.isSelected = music.isFavorite()
            binding.musicItemFavorite.imageTintList = ColorStateList.valueOf(
                if (music.isFavorite()) accentColor else palette.messageColor
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
            onClick = { onTrackClicked(position) },
            onFavoriteClick = { onToggleFavorite(queue[position]) },
            onMenuClick = { onTrackMenu(queue[position]) }
        )
    }
}


