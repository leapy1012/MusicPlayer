package gd.app.musicplayer.ui.common.base

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import gd.app.musicplayer.R
import gd.app.musicplayer.core.ui.extension.appContainer
import gd.app.musicplayer.core.theme.*
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.isFavorite
import gd.app.musicplayer.databinding.DialogQueueListBinding
import gd.app.musicplayer.databinding.DialogQueueListItemBinding
import gd.app.musicplayer.playback.MusicPlaybackState
import gd.app.musicplayer.ui.common.playback.PlaybackControlViewModel
import gd.app.musicplayer.feature.playlist.ActivityPlaylistSelect
import gd.app.musicplayer.feature.selection.DragSwipeCallback
import gd.app.musicplayer.feature.selection.ItemMoveListener
import gd.app.musicplayer.feature.selection.ItemTouchStateListener
import gd.app.musicplayer.ui.theme.applyCurrentTheme
import kotlinx.coroutines.launch
import java.util.Collections

@AndroidEntryPoint
class PlaybackQueueBottomSheetFragment : BottomSheetDialogFragment() {
    private val viewModel: PlaybackControlViewModel by viewModels()
    private val toggleFavoriteTrack by lazy { requireContext().appContainer.toggleFavoriteTrackUseCase }
    private val themeRepo by lazy { requireContext().appContainer.themeRepo }

    private var _binding: DialogQueueListBinding? = null
    private val binding: DialogQueueListBinding
        get() = requireNotNull(_binding)

    private lateinit var adapter: QueueAdapter
    private var playbackState: MusicPlaybackState = MusicPlaybackState()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogQueueListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.root.background = view.context.appContainer.themeRepo
            .getCorePalette(view.context)
            .getDialogSurfaceDrawable(view.context)
        applyCurrentTheme(view)

        adapter = QueueAdapter(
            onTrackClicked = { position ->
                val queue = playbackState.queue
                if (position !in queue.indices) return@QueueAdapter
                viewModel.playQueue(requireContext(), queue, position)
                dismissAllowingStateLoss()
            },
            onTrackRemoved = ::removeQueueItem,
            onTrackMoved = ::replaceQueuePreservingCurrentTrack,
            onToggleFavorite = ::toggleFavorite
        )

        binding.currentListRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.currentListRecycler.adapter = adapter

        binding.currentListClose.setOnClickListener {
            dismissAllowingStateLoss()
        }
        binding.currentListSave.setOnClickListener {
            val queue = playbackState.queue
            if (queue.isEmpty()) return@setOnClickListener
            ActivityPlaylistSelect.start(requireContext(), queue)
            dismissAllowingStateLoss()
        }
        binding.currentListDelete.setOnClickListener {
            if (playbackState.queue.isEmpty()) return@setOnClickListener
            viewModel.clearQueue(requireContext())
            dismissAllowingStateLoss()
        }
        binding.currentListMode.setOnClickListener {
            shuffleQueueKeepingCurrentTrack()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.playbackState.collect(::render)
        }
    }

    override fun onStart() {
        super.onStart()

        val bottomSheet =
            dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?: return
        val params = bottomSheet.layoutParams
        params.height = calculateDialogHeight(requireContext())
        bottomSheet.layoutParams = params
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun render(state: MusicPlaybackState) {
        playbackState = state
        binding.currentListTitle.text = getString(R.string.music_queue, state.queue.size)
        binding.currentListSave.isEnabled = state.queue.isNotEmpty()
        binding.currentListDelete.isEnabled = state.queue.isNotEmpty()
        binding.currentListRecycler.isVisible = state.queue.isNotEmpty()
        adapter.submitQueue(state.queue, state.currentIndex)
    }

    private fun removeQueueItem(position: Int) {
        val currentQueue = playbackState.queue
        if (position !in currentQueue.indices) return

        val updatedQueue = currentQueue.toMutableList().apply { removeAt(position) }
        if (updatedQueue.isEmpty()) {
            viewModel.clearQueue(requireContext())
            dismissAllowingStateLoss()
            return
        }

        val currentIndex = playbackState.currentIndex
        val nextIndex = when {
            position < currentIndex -> currentIndex - 1
            position > currentIndex -> currentIndex
            position >= updatedQueue.size -> updatedQueue.lastIndex
            else -> position
        }.coerceIn(0, updatedQueue.lastIndex)

        viewModel.replaceQueue(requireContext(), updatedQueue, nextIndex)
    }

    private fun replaceQueuePreservingCurrentTrack(updatedQueue: List<Music>) {
        if (updatedQueue.isEmpty()) {
            viewModel.clearQueue(requireContext())
            dismissAllowingStateLoss()
            return
        }

        val currentTrackId = playbackState.currentTrack?._id
        val nextIndex = updatedQueue.indexOfFirst { it._id == currentTrackId }
            .takeIf { it >= 0 }
            ?: playbackState.currentIndex.coerceIn(0, updatedQueue.lastIndex)

        viewModel.replaceQueue(requireContext(), updatedQueue, nextIndex)
    }

    private fun shuffleQueueKeepingCurrentTrack() {
        val queue = playbackState.queue
        if (queue.size < 2) return

        val currentTrack = playbackState.currentTrack
        val shuffledQueue = if (currentTrack == null) {
            queue.shuffled()
        } else {
            buildList(queue.size) {
                add(currentTrack)
                addAll(queue.filterNot { it._id == currentTrack._id }.shuffled())
            }
        }

        viewModel.replaceQueue(requireContext(), shuffledQueue, 0)
        ToastUtil.show(requireContext(), R.string.shuffle)
    }

    private fun toggleFavorite(track: Music) {
        viewLifecycleOwner.lifecycleScope.launch {
            val favorited = toggleFavoriteTrack(track._id)
            adapter.updateFavorite(track._id, favorited)
        }
    }

    private fun calculateDialogHeight(context: Context): Int {
        val configuration = context.resources.configuration
        val screenHeight = context.resources.displayMetrics.heightPixels

        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val isTablet =
            (configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >=
                Configuration.SCREENLAYOUT_SIZE_LARGE

        val ratio = if (isLandscape || isTablet) 0.72f else 0.60f
        return (screenHeight * ratio).toInt()
    }

    companion object {
        fun show(manager: FragmentManager) {
            PlaybackQueueBottomSheetFragment()
                .show(manager, PlaybackQueueBottomSheetFragment::class.java.simpleName)
        }
    }
}

private class QueueAdapter(
    private val onTrackClicked: (Int) -> Unit,
    private val onTrackRemoved: (Int) -> Unit,
    private val onTrackMoved: (List<Music>) -> Unit,
    private val onToggleFavorite: (Music) -> Unit
) : RecyclerView.Adapter<QueueAdapter.QueueViewHolder>(), ItemMoveListener {

    private val queue = mutableListOf<Music>()
    private var currentIndex = -1
    private lateinit var recyclerView: RecyclerView
    private lateinit var itemTouchHelper: ItemTouchHelper

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
        val index = queue.indexOfFirst { it._id == trackId }
        if (index < 0) return
        queue[index] = queue[index].copy(p_id = if (favorited) 1L else 0L)
        notifyItemChanged(index)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
        val callback = DragSwipeCallback(null).apply {
            setLongPressDragEnabled(false)
            setDragDirections(ItemTouchHelper.UP or ItemTouchHelper.DOWN)
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    override fun getItemId(position: Int): Long = queue[position]._id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val binding = DialogQueueListItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return QueueViewHolder(binding)
    }

    override fun getItemCount(): Int = queue.size

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        holder.bind(
            music = queue[position],
            isCurrent = position == currentIndex,
            onClick = { onTrackClicked(position) },
            onRemove = { onTrackRemoved(position) },
            onFavoriteClick = { onToggleFavorite(queue[position]) },
            onDragStart = { itemTouchHelper.startDrag(holder) }
        )
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition !in queue.indices || toPosition !in queue.indices) return
        Collections.swap(queue, fromPosition, toPosition)
        onTrackMoved(queue.toList())
    }

    class QueueViewHolder(
        private val binding: DialogQueueListItemBinding
    ) : RecyclerView.ViewHolder(binding.root), ItemTouchStateListener {

        fun bind(
            music: Music,
            isCurrent: Boolean,
            onClick: () -> Unit,
            onRemove: () -> Unit,
            onFavoriteClick: () -> Unit,
            onDragStart: () -> Unit
        ) {
            val context = binding.root.context
            val palette = context.appContainer.themeRepo
                .getCorePalette(context)
            val titleColor = if (isCurrent) {
                context.appContainer.themeRepo.getAccentColor(context)
            } else {
                palette.titleColor
            }
            val artistColor = if (isCurrent) titleColor else palette.messageColor

            binding.currentListMusicTitle.text = music.title
            binding.currentListMusicArtist.text = music.artist
            binding.currentListMusicTitle.setTextColor(titleColor)
            binding.currentListMusicArtist.setTextColor(artistColor)
            binding.currentListFavorite.isSelected = music.isFavorite()
            binding.currentListFavorite.imageTintList = ColorStateList.valueOf(
                if (music.isFavorite()) context.appContainer.themeRepo.getAccentColor(context)
                else palette.titleColor
            )

            binding.root.alpha = if (isCurrent) 1f else 0.92f
            binding.root.setOnClickListener { onClick() }
            binding.currentListRemove.setOnClickListener { onRemove() }
            binding.currentListFavorite.setOnClickListener { onFavoriteClick() }
            binding.musicItemDrag.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onDragStart()
                }
                false
            }
        }

        override fun onItemSelected() {
            binding.root.alpha = 0.7f
        }

        override fun onItemCleared() {
            binding.root.alpha = 1f
        }
    }
}
