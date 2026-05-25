package gd.app.musicplayer.feature.lock

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
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
import gd.app.musicplayer.core.designsystem.dialog.BaseBottomSheetDialogFragment
import gd.app.musicplayer.ui.common.base.PlaybackQueueBottomSheetEvent
import gd.app.musicplayer.ui.common.base.PlaybackQueueBottomSheetUiState
import gd.app.musicplayer.ui.common.base.PlaybackQueueBottomSheetViewModel
import gd.app.musicplayer.ui.common.base.RecyclerEmptyStateController
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LockPlaybackQueueDialogFragment : BaseBottomSheetDialogFragment() {

    @Inject lateinit var themeRepo: ThemeRepo

    private val viewModel: PlaybackQueueBottomSheetViewModel by viewModels()

    private var _binding: DialogLockScreenQueueBinding? = null
    private val binding: DialogLockScreenQueueBinding
        get() = requireNotNull(_binding)

    private lateinit var adapter: LockQueueAdapter
    private lateinit var emptyStateController: RecyclerEmptyStateController
    private var latestState = PlaybackQueueBottomSheetUiState()

    override fun onCreateBottomSheetView(
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
            emptyViewStub = binding.layoutListEmpty
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
        val previousTrackId = latestState.currentMusic?.id
        latestState = state

        renderCount(state)
        renderArtwork(state.currentMusic)
        emptyStateController.setVisible(state.queue.isEmpty())

        adapter.submitQueue(
            items = state.queue,
            currentTrackId = state.currentTrackId
        )

        if (previousTrackId != state.currentMusic?.id) {
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
        val bottomSheetDialog = dialog as? BottomSheetDialog ?: return
        val window = bottomSheetDialog.window ?: return
        val bottomSheet = bottomSheetDialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        val coordinator = bottomSheetDialog.findViewById<View>(
            com.google.android.material.R.id.coordinator
        )
        val container = bottomSheetDialog.findViewById<View>(
            com.google.android.material.R.id.container
        )

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.navigationBarColor = Color.TRANSPARENT
        window.statusBarColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

        window.decorView.setBackgroundColor(Color.TRANSPARENT)
        listOfNotNull(container, coordinator, bottomSheet).forEach { view ->
            view.fitsSystemWindows = false
            view.setBackgroundColor(Color.TRANSPARENT)
        }
        binding.root.fitsSystemWindows = false
        binding.root.setBackgroundColor(Color.TRANSPARENT)

        bottomSheetDialog.behavior.isGestureInsetBottomIgnored = true
        bottomSheetDialog.behavior.skipCollapsed = true
        bottomSheetDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED

        window.attributes = window.attributes.apply {
            dimAmount = 0.6f
        }
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        /*
         * BaseBottomSheetDialogFragment pads the root above navigation bars for
         * ordinary sheets. The lock-screen queue must behave like the original
         * full-screen lock overlay: its background continues behind the system
         * navigation area, while row content remains in the sheet.
         */
        bottomSheet.post {
            installLockScreenInsetsHandler(
                window = window,
                bottomSheetDialog = bottomSheetDialog,
                bottomSheet = bottomSheet
            )
        }
    }

    private fun installLockScreenInsetsHandler(
        window: android.view.Window,
        bottomSheetDialog: BottomSheetDialog,
        bottomSheet: View
    ) {
        val baseHeight = calculateDialogHeight()

        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
            val navBottom = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            ).bottom
            val targetHeight = baseHeight + navBottom

            binding.root.setPadding(0, 0, 0, 0)
            binding.dialogRecycler.clipToPadding = false
            binding.dialogRecycler.setPadding(
                binding.dialogRecycler.paddingLeft,
                binding.dialogRecycler.paddingTop,
                binding.dialogRecycler.paddingRight,
                navBottom
            )

            applyBottomSheetHeight(
                bottomSheet = bottomSheet,
                height = targetHeight
            )
            bottomSheetDialog.behavior.peekHeight = targetHeight
            bottomSheetDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED

            WindowInsetsCompat.CONSUMED
        }

        applyBottomSheetHeight(
            bottomSheet = bottomSheet,
            height = baseHeight
        )
        bottomSheetDialog.behavior.peekHeight = baseHeight
        ViewCompat.requestApplyInsets(window.decorView)
    }

    private fun applyBottomSheetHeight(
        bottomSheet: View,
        height: Int
    ) {
        bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
            this.height = height
        }
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

private class LockQueueAdapter(
    private val onTrackClicked: (Int) -> Unit,
    private val onTrackRemoved: (Int) -> Unit,
    private val onToggleFavorite: (Music) -> Unit
) : RecyclerView.Adapter<LockQueueAdapter.LockQueueViewHolder>() {

    private companion object {
        const val PAYLOAD_CURRENT = "payload_current"
    }

    private val queue = mutableListOf<Music>()
    private var currentTrackId: Long? = null

    init {
        setHasStableIds(true)
    }

    fun submitQueue(items: List<Music>, currentTrackId: Long?) {
        val oldCurrentIndex = currentIndex()
        queue.clear()
        queue.addAll(items)

        val currentChanged = this.currentTrackId != currentTrackId
        this.currentTrackId = currentTrackId

        notifyDataSetChanged()

        if (currentChanged) {
            notifyCurrentChanged(oldCurrentIndex)
            notifyCurrentChanged(currentIndex())
        }
    }

    override fun getItemId(position: Int): Long = queue[position].id

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
                holder.bindCurrentState(isCurrent = queue[position].id == currentTrackId)
            }
            else -> super.onBindViewHolder(holder, position, payloads)
        }
    }

    private fun currentIndex(): Int = queue.indexOfFirst { it.id == currentTrackId }

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

            bindCurrentState(isCurrent)
            bindFavorite(music)

            binding.root.setOnClickListener { onClick() }
            binding.musicItemRemove.setOnClickListener { onRemove() }
            binding.musicItemFavorite.setOnClickListener { onFavoriteClick() }
        }

        fun bindCurrentState(isCurrent: Boolean) {
            val titleColor = if (isCurrent) {
                Color.rgb(255, 90, 90)
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
