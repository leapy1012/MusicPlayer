package gd.app.musicplayer.feature.lock

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.coui.appcompat.panel.COUIBottomSheetDialog
import android.app.Dialog
import android.content.Context
import androidx.appcompat.view.ContextThemeWrapper
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.albumArtSource
import gd.app.musicplayer.core.common.extension.dpToPx
import gd.app.musicplayer.core.common.extension.getMaxScreenSize
import gd.app.musicplayer.core.common.extension.isFavorite
import gd.app.musicplayer.core.common.extension.isLandscape
import gd.app.musicplayer.core.common.extension.isRtl
import gd.app.musicplayer.core.common.extension.isTablet
import gd.app.musicplayer.core.common.extension.screenWidth
import gd.app.musicplayer.databinding.DialogLockScreenQueueBinding
import gd.app.musicplayer.databinding.DialogLockScreenQueueItemBinding
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.repository.ThemeRepo
import gd.app.musicplayer.ui.common.base.PlaybackQueueBottomSheetEvent
import gd.app.musicplayer.ui.common.base.PlaybackQueueBottomSheetUiState
import gd.app.musicplayer.ui.common.base.PlaybackQueueBottomSheetViewModel
import gd.app.musicplayer.ui.common.base.RecyclerEmptyStateController
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LockPlaybackQueueDialogFragment : BottomSheetDialogFragment() {

    @Inject lateinit var themeRepo: ThemeRepo

    private val viewModel: PlaybackQueueBottomSheetViewModel by viewModels()

    private var _binding: DialogLockScreenQueueBinding? = null
    private val binding: DialogLockScreenQueueBinding
        get() = requireNotNull(_binding)

    private lateinit var adapter: LockQueueAdapter
    private lateinit var emptyStateController: RecyclerEmptyStateController
    private var latestState = PlaybackQueueBottomSheetUiState()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        LockQueueDialog(ContextThemeWrapper(requireContext(), com.coui.appcompat.R.style.Theme_COUI_Main_Dark))

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogLockScreenQueueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = LockQueueAdapter(
            accentColorProvider = themeRepo::getAccentColor,
            onTrackClicked = { position ->
                viewModel.playQueueAt(position, false)
            },
            onTrackRemoved = { position ->
                viewModel.removeQueueItem(position, latestState)
            },
            onToggleFavorite = viewModel::toggleFavorite
        )

        binding.dialogRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.dialogRecycler.adapter = adapter
        (binding.dialogRecycler.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        emptyStateController = RecyclerEmptyStateController(
            recyclerView = binding.dialogRecycler,
            emptyViewStub = binding.layoutListEmpty,
            onDarkSurface = true
        ).apply {
            setEmptyMessage(getString(R.string.music_empty))
            setActionButtonVisible(false)
            setExtraTextVisible(false)
            applyTheme(themeRepo.getCorePalette())
        }

        binding.dialogBack.setOnClickListener {
            dismissAllowingStateLoss()
        }

        binding.dialogBackground.setImageDrawable(
            themeRepo.getCorePalette().getActivityBackgroundDrawable(requireContext())
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect(::render)
                }
                launch {
                    viewModel.events.collect(::handleEvent)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        configureBottomSheet()
    }

    override fun onDestroyView() {
        binding.dialogRecycler.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private fun render(state: PlaybackQueueBottomSheetUiState) {
        val previousCurrentIndex = latestState.currentIndex
        latestState = state

        renderCount(state)
        renderArtwork(state.currentMusic)
        emptyStateController.setVisible(state.queue.isEmpty())

        adapter.submitQueue(
            items = state.queue,
            currentIndex = state.currentIndex
        )

        if (previousCurrentIndex != state.currentIndex) {
            scrollToCurrent(state)
        }
    }

    private fun renderCount(state: PlaybackQueueBottomSheetUiState) {
        val total = state.queue.size
        val current = if (total > 0) {
            (state.currentIndex + 1).coerceIn(1, total)
        } else {
            0
        }
        binding.dialogCount.text = getString(R.string.lock_queue_count_format, current, total)
    }

    private fun renderArtwork(track: Music?) {
        if (track == null) {
            binding.dialogImage.setImageDrawable(null)
            return
        }

        Glide.with(this)
            .load(track.albumArtSource())
            .placeholder(R.drawable.th_music_large)
            .error(R.drawable.th_music_large)
            .centerCrop()
            .into(binding.dialogImage)
    }

    private fun scrollToCurrent(state: PlaybackQueueBottomSheetUiState) {
        val currentIndex = state.currentIndex
        if (currentIndex !in state.queue.indices) return

        binding.dialogRecycler.post {
            (binding.dialogRecycler.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(currentIndex, 0)
        }
    }

    private fun handleEvent(event: PlaybackQueueBottomSheetEvent) {
        when (event) {
            PlaybackQueueBottomSheetEvent.Dismiss -> dismissAllowingStateLoss()
            is PlaybackQueueBottomSheetEvent.ShowToast -> Unit
        }
    }

    private fun configureBottomSheet() {
        (dialog as? LockQueueDialog)?.configurePanel(calculateDialogHeight())
        binding.root.setPadding(0, 0, 0, 0)
    }

    private fun calculateDialogHeight(): Int {
        val ratio = if (requireContext().isLandscape() || requireContext().isTablet()) {
            0.72f
        } else {
            0.60f
        }
        return (requireContext().getMaxScreenSize(includeSystemDecor = true) * ratio).toInt()
    }

    companion object {
        fun show(manager: FragmentManager) {
            LockPlaybackQueueDialogFragment()
                .show(manager, LockPlaybackQueueDialogFragment::class.java.simpleName)
        }
    }
}

/** No light navigation strip or extra COUI handle above the lock player's own queue header. */
internal class LockQueueDialog(context: Context) : COUIBottomSheetDialog(context) {
    internal fun configurePanel(heightPx: Int) {
        setShouldRegisterWindowInsetsListener(false)
        hideDragView()
        setShowInDialogFragment(true)
        setPanelBackground(ColorDrawable(Color.TRANSPARENT))
        setHeightChangeAnim(false)
        setHeight(heightPx)
        // The content has a fixed height; the behavior's outer child must wrap it.
        // A MATCH_PARENT outer child is expanded at y=0 even when its content is shorter.
        findViewById<View>(com.coui.appcompat.R.id.design_bottom_sheet)?.let { outer ->
            outer.layoutParams = outer.layoutParams.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
        }
        behavior.isFitToContents = true
        behavior.isGestureInsetBottomIgnored = true
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes = window.attributes.apply { dimAmount = 0.6f }
            LockPlayerWindow.apply(window)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window?.let(LockPlayerWindow::apply)
    }
}

private class LockQueueAdapter(
    private val accentColorProvider: () -> Int,
    private val onTrackClicked: (Int) -> Unit,
    private val onTrackRemoved: (Int) -> Unit,
    private val onToggleFavorite: (Music) -> Unit
) : RecyclerView.Adapter<LockQueueAdapter.LockQueueViewHolder>() {

    private companion object {
        const val PAYLOAD_CURRENT = "payload_current"
    }

    private val queue = mutableListOf<Music>()
    private var currentIndex: Int = RecyclerView.NO_POSITION

    fun submitQueue(items: List<Music>, currentIndex: Int) {
        val oldCurrentIndex = currentIndex()
        queue.clear()
        queue.addAll(items)

        val currentChanged = this.currentIndex != currentIndex
        this.currentIndex = currentIndex

        notifyDataSetChanged()

        if (currentChanged) {
            notifyCurrentChanged(oldCurrentIndex)
            notifyCurrentChanged(currentIndex())
        }
    }

    override fun getItemCount(): Int = queue.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LockQueueViewHolder {
        return LockQueueViewHolder(
            DialogLockScreenQueueItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: LockQueueViewHolder, position: Int) {
        holder.bind(
            music = queue[position],
            position = position,
            isCurrent = position == currentIndex(),
            accentColor = accentColorProvider(),
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
            }
        )
    }

    override fun onBindViewHolder(
        holder: LockQueueViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        when {
            payloads.contains(PAYLOAD_CURRENT) -> {
                holder.bindCurrentState(
                    isCurrent = position == currentIndex(),
                    accentColor = accentColorProvider()
                )
            }
            else -> super.onBindViewHolder(holder, position, payloads)
        }
    }

    private fun currentIndex(): Int {
        return if (currentIndex in queue.indices) currentIndex else RecyclerView.NO_POSITION
    }

    private fun notifyCurrentChanged(position: Int) {
        if (position in queue.indices) {
            notifyItemChanged(position, PAYLOAD_CURRENT)
        }
    }

    class LockQueueViewHolder(
        private val binding: DialogLockScreenQueueItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            val maxTitleWidth = ((binding.root.context.screenWidth - binding.root.context.dpToPx(128f)) * 0.66f)
                .toInt()
                .coerceAtLeast(binding.root.context.dpToPx(80f))
            binding.musicItemTitle.maxWidth = maxTitleWidth
        }

        fun bind(
            music: Music,
            position: Int,
            isCurrent: Boolean,
            accentColor: Int,
            onClick: () -> Unit,
            onRemove: () -> Unit,
            onFavoriteClick: () -> Unit
        ) {
            val number = position + 1
            val context = binding.root.context

            if (context.isRtl()) {
                binding.musicItemTitle.text = "${music.title}. $number"
                binding.musicItemArtist.text = "${music.artist} - "
            } else {
                binding.musicItemTitle.text = "$number. ${music.title}"
                binding.musicItemArtist.text = " - ${music.artist}"
            }

            bindCurrentState(isCurrent, accentColor)
            bindFavorite(music)

            binding.root.setOnClickListener { onClick() }
            binding.musicItemRemove.setOnClickListener { onRemove() }
            binding.musicItemFavorite.setOnClickListener { onFavoriteClick() }
        }

        fun bindCurrentState(isCurrent: Boolean, accentColor: Int) {
            val titleColor = if (isCurrent) {
                accentColor
            } else {
                Color.WHITE
            }
            val artistColor = if (isCurrent) titleColor else Color.parseColor("#b3ffffff")

            binding.musicItemTitle.setTextColor(titleColor)
            binding.musicItemArtist.setTextColor(artistColor)
            binding.musicItemTitle.isSelected = isCurrent
            binding.musicItemArtist.isSelected = isCurrent
        }

        fun bindFavorite(music: Music) {
            binding.musicItemFavorite.isSelected = music.isFavorite()
        }
    }
}
