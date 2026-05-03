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
import gd.app.musicplayer.core.extension.appDependencies
import gd.app.musicplayer.core.theme.*
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.core.extension.isFavorite
import gd.app.musicplayer.core.ui.dialog.BaseBottomSheetDialogFragment
import gd.app.musicplayer.databinding.DialogQueueListBinding
import gd.app.musicplayer.databinding.DialogQueueListItemBinding
import gd.app.musicplayer.ui.feature.selection.ItemMoveListener
import gd.app.musicplayer.ui.feature.selection.ItemTouchStateListener
import gd.app.musicplayer.ui.theme.applyCurrentTheme
import kotlinx.coroutines.launch
import java.util.Collections

@AndroidEntryPoint
class PlaybackQueueBottomSheetFragment : BaseBottomSheetDialogFragment() {
    private val viewModel: PlaybackQueueBottomSheetViewModel by viewModels()

    private var _binding: DialogQueueListBinding? = null
    private val binding: DialogQueueListBinding
        get() = requireNotNull(_binding)

    private lateinit var adapter: QueueAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.root.background = view.context.appDependencies.themeRepo
            .getCorePalette(view.context)
            .getDialogSurfaceDrawable(view.context)
        applyCurrentTheme(view)

        adapter = QueueAdapter(
            onTrackClicked = { /* position -> viewModel.playQueueAt(position, playbackState) */ },
            onTrackRemoved = { /* position -> viewModel.removeQueueItem(position, playbackState) */ },
            onTrackMoved = { /* queue -> viewModel.replaceQueuePreservingCurrentTrack(queue, playbackState) */},
            onToggleFavorite = viewModel::toggleFavorite
        )

        binding.currentListRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.currentListRecycler.adapter = adapter
        (binding.currentListRecycler.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        binding.currentListClose.setOnClickListener {
            dismissAllowingStateLoss()
        }
//        binding.currentListSave.setOnClickListener {
//            val queue = viewModel.saveQueueToPlaylist(playbackState) ?: return@setOnClickListener
//            ActivityPlaylistSelect.start(requireContext(), queue)
//        }
//        binding.currentListDelete.setOnClickListener {
//            if (playbackState.queue.isEmpty()) return@setOnClickListener
//            val config = MessageDialog.Config.create(requireContext()).apply {
//                titleText = getString(R.string.clear)
//                messageText = getString(R.string.clear_message)
//                negativeButtonText = getString(R.string.cancel)
//                positiveButtonText = getString(R.string.clear)
//                positiveButtonClickListener = DialogInterface.OnClickListener { dialog, _ ->
//                    viewModel.clearQueueOrDismiss(playbackState)
//                    dialog.dismiss()
//                }
//            }
//            MessageDialog.show(requireActivity(), config)
//        }
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
                    }
                }
            }
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
        binding.currentListTitle.text = getString(R.string.music_queue, state.queue.size)
        binding.currentListSave.isEnabled = state.queue.isNotEmpty()
        binding.currentListDelete.isEnabled = state.queue.isNotEmpty()
        binding.currentListRecycler.isVisible = state.queue.isNotEmpty()
        adapter.submitQueue(state.queue, state.currentIndex)
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
    private val onTrackClicked: (Int) -> Unit,
    private val onTrackRemoved: (Int) -> Unit,
    private val onTrackMoved: (List<Music>) -> Unit,
    private val onToggleFavorite: (Music) -> Unit
) : RecyclerView.Adapter<QueueAdapter.QueueViewHolder>(), ItemMoveListener {
    private companion object {
        const val PAYLOAD_FAVORITE = "payload_favorite"
        const val FAVORITES_PLAYLIST_ID = 1L
    }

    private val queue = mutableListOf<Music>()
    private val favoriteOverrides = mutableMapOf<Long, Boolean>()
    private var currentIndex = -1
    private lateinit var recyclerView: RecyclerView
    private lateinit var itemTouchHelper: ItemTouchHelper
    private var dragChanged = false

    init {
        setHasStableIds(true)
    }

    fun submitQueue(items: List<Music>, currentIndex: Int) {
        queue.clear()
        queue.addAll(
            items.map { music ->
                val overriddenFavorite = favoriteOverrides[music.id] ?: return@map music
                music.copy(playlistId = if (overriddenFavorite) FAVORITES_PLAYLIST_ID else 0L)
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
        queue[index] = queue[index].copy(playlistId = if (favorited) FAVORITES_PLAYLIST_ID else 0L)
        notifyItemChanged(index, PAYLOAD_FAVORITE)
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
                if (dragChanged) {
                    dragChanged = false
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

    override fun onBindViewHolder(
        holder: QueueViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_FAVORITE)) {
            holder.bindFavorite(queue[position])
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition !in queue.indices || toPosition !in queue.indices) return
        Collections.swap(queue, fromPosition, toPosition)
        dragChanged = true
        notifyItemMoved(fromPosition, toPosition)
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
            val palette = context.appDependencies.themeRepo
                .getCorePalette(context)
            val titleColor = if (isCurrent) {
                context.appDependencies.themeRepo.getAccentColor(context)
            } else {
                palette.titleColor
            }
            val artistColor = if (isCurrent) titleColor else palette.messageColor

            binding.currentListMusicTitle.text = music.title
            binding.currentListMusicArtist.text = " - " + music.artist
            binding.currentListMusicTitle.setTextColor(titleColor)
            binding.currentListMusicArtist.setTextColor(artistColor)
            bindFavorite(music)

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

        fun bindFavorite(music: Music) {
            val context = binding.root.context
            val palette = context.appDependencies.themeRepo.getCorePalette(context)
            val isFavorite = music.isFavorite()
            binding.currentListFavorite.isSelected = isFavorite
            binding.currentListFavorite.imageTintList = ColorStateList.valueOf(
                if (isFavorite) context.appDependencies.themeRepo.getAccentColor(context)
                else palette.titleColor
            )
        }

        override fun onItemSelected() {
            binding.root.alpha = 0.7f
        }

        override fun onItemCleared() {
            binding.root.alpha = 1f
        }
    }
}

