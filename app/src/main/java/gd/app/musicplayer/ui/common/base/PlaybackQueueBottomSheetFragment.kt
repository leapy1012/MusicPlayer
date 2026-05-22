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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fueled.draggablerecyclerview.DragItemTouchHelperCallback
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.designsystem.theme.ThemePalette
import gd.app.musicplayer.core.designsystem.theme.messageColor
import gd.app.musicplayer.core.designsystem.theme.titleColor
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
            applyTheme = { itemView ->
                (activity as? BaseActivity)?.applyThemeTo(itemView)
            },
            onTrackClicked = { position -> viewModel.playQueueAt(position)},
            onTrackRemoved = { position -> viewModel.removeQueueItem(position, playbackState) },
            onTrackMoved = { queue -> viewModel.replaceQueuePreservingCurrentTrack(queue, playbackState) },
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
        }

        binding.currentListMode.setOnClickListener {
            viewModel.cyclePlayMode()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch {
                    viewModel.events.collect(::handleEvent)
                }
                launch {
                    viewModel.playModeUiState.collect { state ->
                        binding.currentListMode.setImageResource(state.iconRes)
                        (activity as? BaseActivity)?.applyThemeTo(binding.currentListMode)
                    }
                }
            }
        }
    }

    private fun showClearQueueDialog() {
        if (parentFragmentManager.findFragmentByTag(QueueClearConfirmDialogFragment.TAG) != null) {
            dismissAllowingStateLoss()
            return
        }

        QueueClearConfirmDialogFragment()
            .show(parentFragmentManager, QueueClearConfirmDialogFragment.TAG)
        dismissAllowingStateLoss()
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) {
            adapter.refreshTheme()
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
        adapter.submitQueue(state.queue, state.currentTrackId)
    }

    private fun handleEvent(event: PlaybackQueueBottomSheetEvent) {
        when (event) {
            PlaybackQueueBottomSheetEvent.Dismiss -> dismissAllowingStateLoss()
            is PlaybackQueueBottomSheetEvent.FavoriteChanged -> {
                adapter.updateFavorite(event.trackId, event.favorited)
            }
            is PlaybackQueueBottomSheetEvent.ShowToast -> {
                ToastUtil.show(requireContext(), event.messageRes)
            }
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
    private val themeProvider: () -> ThemePalette,
    private val accentColorProvider: () -> Int,
    private val applyTheme: (View) -> Unit,
    private val onTrackClicked: (Int) -> Unit,
    private val onTrackRemoved: (Int) -> Unit,
    private val onTrackMoved: (List<Music>) -> Unit,
    private val onToggleFavorite: (Music) -> Unit
) : RecyclerView.Adapter<QueueAdapter.QueueViewHolder>(), ItemMoveListener {
    private companion object {
        const val PAYLOAD_FAVORITE = "payload_favorite"
        const val PAYLOAD_CURRENT = "payload_current"
        const val FAVORITES_PLAYLIST_ID = 1L
    }

    private val queue = mutableListOf<Music>()
    private val favoriteOverrides = mutableMapOf<Long, Boolean>()
    private var currentTrackId: Long? = null
    private var isDragging = false
    private lateinit var recyclerView: RecyclerView
    private lateinit var itemTouchHelper: ItemTouchHelper
    private var dragChanged = false

    init {
        setHasStableIds(true)
    }

    fun submitQueue(items: List<Music>, currentTrackId: Long?) {
        // Do not let a stale playback emission overwrite the user's local drag order.
        // The final order is committed in onDragFinishedListener.
        if (isDragging) {
            this.currentTrackId = currentTrackId
            return
        }

        val previousCurrentIndex = currentIndex()
        queue.clear()
        queue.addAll(
            items.map { music ->
                val overriddenFavorite = favoriteOverrides[music.id] ?: return@map music
                music.copy(playlistId = if (overriddenFavorite) FAVORITES_PLAYLIST_ID else 0L)
            }
        )
        favoriteOverrides.keys.retainAll(queue.mapTo(hashSetOf()) { it.id })

        val currentChanged = this.currentTrackId != currentTrackId
        this.currentTrackId = currentTrackId

        notifyDataSetChanged()

        if (currentChanged) {
            notifyCurrentChanged(previousCurrentIndex)
            notifyCurrentChanged(currentIndex())
        }
    }

    fun updateFavorite(trackId: Long, isFavorite: Boolean) {
        val index = queue.indexOfFirst { it.id == trackId }
        if (index < 0) return
        favoriteOverrides[trackId] = isFavorite
        queue[index] = queue[index].copy(playlistId = if (isFavorite) FAVORITES_PLAYLIST_ID else 0L)
        notifyItemChanged(index, PAYLOAD_FAVORITE)
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
                    notifyCurrentChanged(currentIndex())
                    onTrackMoved(queue.toList())
                }
            }
            .build()
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    override fun getItemId(position: Int): Long = queue[position].id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val binding = DialogQueueListItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        applyTheme(binding.root)
        return QueueViewHolder(binding)
    }

    override fun getItemCount(): Int = queue.size

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        holder.bind(
            music = queue[position],
            isCurrent = queue[position].id == currentTrackId,
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
            accentColor = accentColorProvider(),
            applyTheme = applyTheme
        )
    }

    override fun onBindViewHolder(
        holder: QueueViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        when {
            payloads.contains(PAYLOAD_FAVORITE) -> {
                holder.bindFavorite(
                    music = queue[position],
                    palette = themeProvider(),
                    accentColor = accentColorProvider(),
                    applyTheme = applyTheme
                )
            }
            payloads.contains(PAYLOAD_CURRENT) -> {
                holder.bindCurrentState(
                    isCurrent = queue[position].id == currentTrackId,
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
        isDragging = true
        dragChanged = true
        notifyItemMoved(fromPosition, toPosition)
        notifyCurrentChanged(fromPosition)
        notifyCurrentChanged(toPosition)
        notifyCurrentChanged(currentIndex())
    }

    private fun currentIndex(): Int = queue.indexOfFirst { it.id == currentTrackId }

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
            accentColor: Int,
            applyTheme: (View) -> Unit
        ) {
            val titleColor = if (isCurrent) {
                accentColor
            } else {
                palette.titleColor
            }
            val artistColor = if (isCurrent) titleColor else palette.messageColor

            binding.currentListMusicTitle.text = music.title
            binding.currentListMusicArtist.text = " - " + music.artist
            binding.currentListMusicTitle.setTextColor(titleColor)
            binding.currentListMusicArtist.setTextColor(artistColor)
            bindFavorite(music, palette, accentColor, applyTheme)

            bindCurrentState(isCurrent, palette, accentColor)
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
            val titleColor = if (isCurrent) accentColor else palette.titleColor
            val artistColor = if (isCurrent) titleColor else palette.messageColor

            binding.currentListMusicTitle.setTextColor(titleColor)
            binding.currentListMusicArtist.setTextColor(artistColor)
            binding.root.alpha = if (isCurrent) 1f else 0.92f
        }

        fun bindFavorite(
            music: Music,
            palette: ThemePalette,
            accentColor: Int,
            applyTheme: (View) -> Unit
        ) {
            val isFavorite = music.isFavorite()
            binding.currentListFavorite.isSelected = isFavorite
            applyTheme(binding.currentListFavorite)
            if (!isFavorite) {
                binding.currentListFavorite.imageTintList = ColorStateList.valueOf(palette.titleColor)
            } else {
                binding.currentListFavorite.imageTintList = ColorStateList.valueOf(accentColor)
            }
            applyTheme(binding.currentListRemove)
            applyTheme(binding.musicItemDrag)
        }

        override fun onItemSelected() {
            binding.root.alpha = 0.7f
        }

        override fun onItemCleared() {
            binding.root.alpha = if (boundIsCurrent) 1f else 0.92f
        }
    }
}

