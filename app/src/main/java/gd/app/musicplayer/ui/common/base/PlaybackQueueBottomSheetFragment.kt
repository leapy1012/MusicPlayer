package gd.app.musicplayer.ui.common.base

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fueled.draggablerecyclerview.DragItemTouchHelperCallback
import dagger.hilt.android.AndroidEntryPoint
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.core.widget.ImageViewCompat
import gd.app.musicplayer.R
import gd.app.musicplayer.core.designsystem.theme.DialogSurfaceColors
import gd.app.musicplayer.core.designsystem.theme.ThemePalette
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.domain.repository.ThemeRepo
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.core.common.extension.isFavorite
import gd.app.musicplayer.core.designsystem.dialog.BaseBottomSheetDialogFragment
import gd.app.musicplayer.databinding.DialogQueueListBinding
import gd.app.musicplayer.databinding.DialogQueueListItemBinding
import gd.app.musicplayer.feature.playlist.PlaylistSelectActivity
import gd.app.musicplayer.ui.selection.ItemMoveListener
import gd.app.musicplayer.ui.selection.ItemTouchStateListener
import javax.inject.Inject
import kotlinx.coroutines.launch
import java.util.Collections

@AndroidEntryPoint
class PlaybackQueueBottomSheetFragment : BaseBottomSheetDialogFragment() {
    private val viewModel: PlaybackQueueBottomSheetViewModel by viewModels()

    @Inject lateinit var themeRepo: ThemeRepo

    private var _binding: DialogQueueListBinding? = null
    private val binding: DialogQueueListBinding
        get() = requireNotNull(_binding)

    private lateinit var adapter: QueueAdapter
    private var playbackState: PlaybackQueueBottomSheetUiState = PlaybackQueueBottomSheetUiState()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = QueueAdapter(
            themeProvider = themeRepo::getCorePalette,
            accentColorProvider = themeRepo::getAccentColor,
            onTrackClicked = { position -> viewModel.playQueueAt(position)},
            onTrackRemoved = { position -> viewModel.removeQueueItem(position, playbackState) },
            onTrackMoved = { queue, currentIndex ->
                viewModel.replaceQueuePreservingCurrentTrack(
                    updatedQueue = queue,
                    state = playbackState,
                    preferredIndex = currentIndex
                )
            },
            onToggleFavorite = viewModel::toggleFavorite
        )

        binding.currentListRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.currentListRecycler.adapter = adapter
        (binding.currentListRecycler.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        binding.currentListClose.setOnClickListener {
            dismissAllowingStateLoss()
        }
        binding.currentListSave.setOnClickListener {
            val queue = viewModel.saveQueueToPlaylist(playbackState) ?: return@setOnClickListener
            PlaylistSelectActivity.start(requireContext(), queue)
        }

        binding.currentListDelete.setOnClickListener {
            if (playbackState.queue.isEmpty()) return@setOnClickListener
            showClearQueueDialog()
            dismissAllowingStateLoss()
        }

        binding.currentListMode.setOnClickListener {
            viewModel.cyclePlayMode()
        }
        paintSheetChrome()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch {
                    viewModel.events.collect(::handleEvent)
                }
                launch {
                    viewModel.playModeUiState.collect { state ->
                        binding.currentListMode.setImageResource(state.iconRes)
                        paintSheetChrome()
                    }
                }
            }
        }
    }

    private fun paintSheetChrome() {
        val palette = themeRepo.getCorePalette()
        val content = DialogSurfaceColors.contentColor(palette)
        val divider = if (DialogSurfaceColors.usesLightPlate(palette)) {
            0x1F000000
        } else {
            0x0DFFFFFF
        }
        val tint = ColorStateList.valueOf(content)
        binding.currentListTitle.setTextColor(content)
        binding.currentListClose.setTextColor(content)
        ImageViewCompat.setImageTintList(binding.currentListMode, tint)
        ImageViewCompat.setImageTintList(binding.currentListSave, tint)
        ImageViewCompat.setImageTintList(binding.currentListDelete, tint)
        binding.currentListHeaderDivider.setBackgroundColor(divider)
        binding.currentListFooterDivider.setBackgroundColor(divider)
    }

    override fun onThemeChanged(palette: ThemePalette?) {
        super.onThemeChanged(palette)
        if (_binding != null && ::adapter.isInitialized) {
            paintSheetChrome()
            adapter.refreshTheme()
        }
    }

    private fun showClearQueueDialog() {
        if (parentFragmentManager.findFragmentByTag(QueueClearConfirmDialogFragment.TAG) != null) {
            return
        }

        QueueClearConfirmDialogFragment()
            .show(parentFragmentManager, QueueClearConfirmDialogFragment.TAG)
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            paintSheetChrome()
        }
        if (::adapter.isInitialized) {
            adapter.refreshTheme()
        }
    }

    override fun onStart() {
        super.onStart()
        if (_binding != null) {
            // Base sheet may re-apply surface after show; retint chrome for light plates.
            paintSheetChrome()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onCreateBottomSheetView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogQueueListBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun render(state: PlaybackQueueBottomSheetUiState) {
        playbackState = state
        binding.currentListTitle.text = getString(R.string.music_queue, state.queue.size)
        binding.currentListSave.isEnabled = state.queue.isNotEmpty()
        binding.currentListDelete.isEnabled = state.queue.isNotEmpty()
        binding.currentListRecycler.isVisible = true
        adapter.submitQueue(
            items = state.queue,
            currentIndex = state.currentIndex
        )
    }

    private fun handleEvent(event: PlaybackQueueBottomSheetEvent) {
        when (event) {
            PlaybackQueueBottomSheetEvent.Dismiss -> dismissAllowingStateLoss()
            is PlaybackQueueBottomSheetEvent.ShowToast -> {
                ToastUtil.show(requireContext(), event.messageRes)
            }
        }
    }

    /**
     * Original `p5.e1.z0`: portrait max-side × 0.60, landscape min-side × 0.72.
     * Applied via [fixedPanelHeightPx] → COUI [COUIBottomSheetDialog.setHeight].
     */
    override fun fixedPanelHeightPx(): Int {
        val configuration = resources.configuration
        val metrics = resources.displayMetrics
        val longSide = maxOf(metrics.widthPixels, metrics.heightPixels)
        val shortSide = minOf(metrics.widthPixels, metrics.heightPixels)
        return if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            (shortSide * 0.72f).toInt()
        } else {
            (longSide * 0.60f).toInt()
        }
    }

    companion object {
        fun show(manager: FragmentManager) {
            PlaybackQueueBottomSheetFragment()
                .show(manager, PlaybackQueueBottomSheetFragment::class.java.simpleName)
        }
    }
}

private class QueueAdapter(
    private val themeProvider: () -> ThemePalette,
    private val accentColorProvider: () -> Int,
    private val onTrackClicked: (Int) -> Unit,
    private val onTrackRemoved: (Int) -> Unit,
    private val onTrackMoved: (List<Music>, Int) -> Unit,
    private val onToggleFavorite: (Music) -> Unit
) : RecyclerView.Adapter<QueueAdapter.QueueViewHolder>(), ItemMoveListener {
    private companion object {
        const val PAYLOAD_CURRENT = "payload_current"
    }

    private val queue = mutableListOf<Music>()
    private var currentIndex: Int = RecyclerView.NO_POSITION
    private var isDragging = false
    private lateinit var recyclerView: RecyclerView
    private lateinit var itemTouchHelper: ItemTouchHelper
    private var dragChanged = false

    fun submitQueue(
        items: List<Music>,
        currentIndex: Int
    ) {
        // Do not let a stale playback emission overwrite the user's local drag order.
        // The final order is committed in onDragFinishedListener.
        if (isDragging) {
            return
        }

        val previousQueue = queue.toList()
        val previousCurrentIndex = currentIndex()
        val currentChanged = this.currentIndex != currentIndex
        this.currentIndex = currentIndex

        val sameOrder = previousQueue == items

        if (sameOrder) {
            queue.clear()
            queue.addAll(items)
            previousQueue.indices.forEach { index ->
                if (previousQueue[index] != queue[index]) {
                    notifyItemChanged(index)
                }
            }
        } else {
            queue.clear()
            queue.addAll(items)
            notifyDataSetChanged()
        }

        if (currentChanged) {
            notifyCurrentChanged(previousCurrentIndex)
            notifyCurrentChanged(currentIndex())
        }
    }

    fun refreshTheme() {
        notifyDataSetChanged()
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
        val callback = DragItemTouchHelperCallback.Builder(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        )
            .setDragEnabled(true)
            .onItemDragListener(::onItemMove)
            .onDragFinishedListener {
                isDragging = false
                if (dragChanged) {
                    dragChanged = false
                    val resolvedCurrentIndex = currentIndex()
                    notifyCurrentChanged(resolvedCurrentIndex)
                    onTrackMoved(queue.toList(), resolvedCurrentIndex)
                }
            }
            .build()
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

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
            isCurrent = isCurrentPosition(position),
            onClick = {
                holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
                    ?.let(onTrackClicked)
            },
            onRemove = {
                holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
                    ?.let(onTrackRemoved)
            },
            onFavoriteClick = {
                holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
                    ?.let { onToggleFavorite(queue[it]) }
            },
            onDragStart = { itemTouchHelper.startDrag(holder) },
            palette = themeProvider(),
            accentColor = accentColorProvider()
        )
    }

    override fun onBindViewHolder(
        holder: QueueViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        when {
            payloads.contains(PAYLOAD_CURRENT) -> {
                holder.bindCurrentState(
                    isCurrent = isCurrentPosition(position),
                    palette = themeProvider(),
                    accentColor = accentColorProvider()
                )
            }
            else -> super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition !in queue.indices || toPosition !in queue.indices) return
        Collections.swap(queue, fromPosition, toPosition)
        if (currentIndex in queue.indices) {
            currentIndex = when {
                fromPosition == currentIndex -> toPosition
                fromPosition < currentIndex && toPosition >= currentIndex -> currentIndex - 1
                fromPosition > currentIndex && toPosition <= currentIndex -> currentIndex + 1
                else -> currentIndex
            }.coerceIn(0, queue.lastIndex)
        }
        isDragging = true
        dragChanged = true
        notifyItemMoved(fromPosition, toPosition)
        notifyCurrentChanged(fromPosition)
        notifyCurrentChanged(toPosition)
        notifyCurrentChanged(currentIndex())
    }

    private fun currentIndex(): Int {
        return if (currentIndex in queue.indices) currentIndex else RecyclerView.NO_POSITION
    }

    private fun isCurrentPosition(position: Int): Boolean {
        return position == currentIndex()
    }

    private fun notifyCurrentChanged(position: Int) {
        if (position in queue.indices) {
            notifyItemChanged(position, PAYLOAD_CURRENT)
        }
    }

    class QueueViewHolder(
        private val binding: DialogQueueListItemBinding
    ) : RecyclerView.ViewHolder(binding.root), ItemTouchStateListener {

        private var boundIsCurrent = false

        fun bind(
            music: Music,
            isCurrent: Boolean,
            onClick: () -> Unit,
            onRemove: () -> Unit,
            onFavoriteClick: () -> Unit,
            onDragStart: () -> Unit,
            palette: ThemePalette,
            accentColor: Int
        ) {
            bindCurrentState(isCurrent, palette, accentColor)
            binding.currentListMusicTitle.text = music.title
            binding.currentListMusicArtist.text = " - " + music.artist
            bindIcons(music, palette, accentColor)

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

        fun bindCurrentState(
            isCurrent: Boolean,
            palette: ThemePalette,
            accentColor: Int
        ) {
            boundIsCurrent = isCurrent
            val content = DialogSurfaceColors.contentColor(palette)
            val secondary = if (DialogSurfaceColors.usesLightPlate(palette)) {
                ColorUtils.setAlphaComponent(content, 0x8A)
            } else {
                ColorUtils.setAlphaComponent(Color.WHITE, 0xB3)
            }
            val titleColor = if (isCurrent) accentColor else content
            val artistColor = if (isCurrent) accentColor else secondary

            binding.currentListMusicTitle.setTextColor(titleColor)
            binding.currentListMusicArtist.setTextColor(artistColor)
            binding.root.alpha = if (isCurrent) 1f else 0.92f
        }

        private fun bindIcons(
            music: Music,
            palette: ThemePalette,
            accentColor: Int
        ) {
            val content = DialogSurfaceColors.contentColor(palette)
            val chrome = ColorStateList.valueOf(content)
            val isFavorite = music.isFavorite()
            binding.currentListFavorite.isSelected = isFavorite
            binding.currentListFavorite.imageTintList = ColorStateList.valueOf(
                if (isFavorite) accentColor else content
            )
            binding.currentListRemove.imageTintList = chrome
            binding.musicItemDrag.imageTintList = chrome
        }

        override fun onItemSelected() {
            binding.root.alpha = 0.7f
        }

        override fun onItemCleared() {
            binding.root.alpha = if (boundIsCurrent) 1f else 0.92f
        }
    }
}

