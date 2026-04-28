package gd.app.musicplayer.ui.feature.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.appDependencies
import gd.app.musicplayer.core.extension.applySystemBarInsets
import gd.app.musicplayer.core.extension.navigateBack
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.core.extension.isFavorite
import gd.app.musicplayer.databinding.FragmentQueueBinding
import gd.app.musicplayer.databinding.MusicPlayFragmentListItemBinding
import gd.app.musicplayer.ui.feature.library.QueueTrackOptionsDialog
import gd.app.musicplayer.ui.feature.playlist.ActivityPlaylistSelect
import gd.app.musicplayer.ui.feature.selection.DragSwipeCallback
import gd.app.musicplayer.ui.feature.selection.ItemMoveListener
import gd.app.musicplayer.ui.feature.selection.ItemTouchStateListener
import gd.app.musicplayer.playback.MusicPlaybackController
import gd.app.musicplayer.playback.PlaybackMode
import gd.app.musicplayer.playback.MusicPlaybackState
import gd.app.musicplayer.core.ui.view.MusicRecyclerView
import gd.app.musicplayer.ui.common.base.RecyclerEmptyStateController
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.ui.common.playback.PlaybackControlViewModel
import gd.app.musicplayer.util.PreferenceUtil
import kotlinx.coroutines.launch
import java.util.Collections

@AndroidEntryPoint
class PlaybackQueueFragment : ViewBindingFragment<FragmentQueueBinding>(),
    Toolbar.OnMenuItemClickListener {

    private val viewModel: PlaybackControlViewModel by viewModels()

    private lateinit var adapter: QueueListAdapter
    private lateinit var emptyStateController: RecyclerEmptyStateController
    private lateinit var recyclerView: MusicRecyclerView
    private lateinit var emptyViewStub: ViewStub
    private var playbackState = MusicPlaybackController.state.value

    override fun onCreateBinding(inflater: LayoutInflater): FragmentQueueBinding =
        FragmentQueueBinding.inflate(inflater)

    override fun onBindingCreated(
        binding: FragmentQueueBinding,
        savedInstanceState: Bundle?
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        recyclerView = binding.root.findViewById(R.id.recyclerview)
        emptyViewStub = binding.root.findViewById(R.id.layout_list_empty)

        setupToolbar(binding)
        adapter = buildAdapter()

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        emptyStateController = RecyclerEmptyStateController(
            recyclerView = recyclerView,
            emptyViewStub = emptyViewStub
        ).apply {
            setEmptyMessage(getString(R.string.music_empty))
        }

        observePlayback()
        refreshPlayModeIcon()
    }

    private fun setupToolbar(binding: FragmentQueueBinding) {
        binding.toolbar.navigateBack(this)
        binding.root.applySystemBarInsets(binding.toolbar, binding.root)
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
                val queue = playbackState.queue
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
                    val binding = requireBinding()
                    adapter.submitQueue(state.queue, state.currentIndex)
                    val isEmpty = state.queue.isEmpty()
                    binding.queueBannerLayout.isGone = isEmpty
                    emptyStateController.setVisible(state.queue.isEmpty())
                    binding.collapsingToolbar.isTitleEnabled = isEmpty.not()
                    updateCollapsingHeight(isEmpty)
                    updateQueueInfo(state)
                    refreshPlayModeIcon()
                }
            }
        }
    }

    private fun updateQueueInfo(state: MusicPlaybackState) {
        val binding = requireBinding()
        val count = state.queue.size
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
    }

    private fun onTrackMovedLocally(updatedQueue: List<Music>) {
        playbackState = playbackState.copy(queue = updatedQueue)
        updateQueueInfo(playbackState)
    }

    private fun updateCollapsingHeight(isEmpty: Boolean) {
        val binding = requireBinding()
        val baseHeight = resources.getDimensionPixelSize(R.dimen.common_title_height)
        val bannerHeight = (40f * resources.displayMetrics.density).toInt()
        val targetHeight = if (isEmpty) baseHeight else baseHeight + bannerHeight
        val params = binding.collapsingToolbar.layoutParams
        if (params.height != targetHeight) {
            params.height = targetHeight
            binding.collapsingToolbar.layoutParams = params
        }
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
                MusicPlaybackController.cyclePlayMode(requireContext())
            }

            R.id.menu_add_to -> {
                if (playbackState.queue.isEmpty()) {
                    ToastUtil.show(requireContext(), R.string.list_is_empty)
                } else {
                    ActivityPlaylistSelect.start(requireContext(), playbackState.queue)
                }
            }
        }
        return true
    }

    private fun refreshPlayModeIcon() {
        val item = requireBinding().toolbar.menu.findItem(R.id.menu_mode) ?: return
        val mode = PreferenceUtil.getInstance(requireContext()).getPlayMode()
        val icon = when (mode) {
            PlaybackMode.LOOP_ALL -> R.drawable.widget_ic_mode_loop
            PlaybackMode.SHUFFLE_ALL -> R.drawable.widget_ic_mode_random
            else -> R.drawable.vector_mode_order
        }
        item.setIcon(icon)
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
    private var currentIndex = -1
    private lateinit var itemTouchHelper: ItemTouchHelper
    private var hasPendingReorder = false

    init {
        setHasStableIds(true)
    }

    fun submitQueue(items: List<Music>, currentIndex: Int) {
        queue.clear()
        queue.addAll(items)
        this.currentIndex = currentIndex
        notifyDataSetChanged()
    }

    fun updateFavorite(trackId: Long, favorited: Boolean) {
        val index = queue.indexOfFirst { it.id == trackId }
        if (index < 0) return
        queue[index] = queue[index].copy(playlistId = if (favorited) 1L else 0L)
        notifyItemChanged(index)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        val callback = DragSwipeCallback(null).apply {
            setLongPressDragEnabled(false)
            setDragDirections(ItemTouchHelper.UP or ItemTouchHelper.DOWN)
        }
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
        hasPendingReorder = true
        onTrackMoved(queue.toList())
    }

    class QueueViewHolder(
        private val binding: MusicPlayFragmentListItemBinding,
        private val itemTouchHelper: ItemTouchHelper
    ) : RecyclerView.ViewHolder(binding.root), ItemTouchStateListener {

        private var onDragCleared: (() -> Unit)? = null

        fun bind(
            music: Music,
            isCurrent: Boolean,
            onClick: () -> Unit,
            onFavoriteClick: () -> Unit,
            onMenuClick: () -> Unit,
            onDragCleared: () -> Unit
        ) {
            this.onDragCleared = onDragCleared
            binding.musicItemTitle.text = music.title
            binding.musicItemExtra.text = music.artist
            binding.musicItemTime.text = MusicPlaybackController.formatTime(music.duration)
            binding.musicItemFavorite.visibility = if (isCurrent) View.VISIBLE else View.GONE
            binding.musicItemFavorite.isSelected = music.isFavorite()
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

        override fun onItemSelected() {
            binding.root.alpha = 0.8f
        }

        override fun onItemCleared() {
            binding.root.alpha = 1f
            onDragCleared?.invoke()
        }
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        holder.bind(
            music = queue[position],
            isCurrent = position == currentIndex,
            onClick = { onTrackClicked(position) },
            onFavoriteClick = { onToggleFavorite(queue[position]) },
            onMenuClick = { onTrackMenu(queue[position]) },
            onDragCleared = {
                if (!hasPendingReorder) return@bind
                hasPendingReorder = false
                onTrackMoveFinished(queue.toList())
            }
        )
    }
}
