package gd.app.musicplayer.ui.drivemode

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.albumArtSource
import gd.app.musicplayer.core.common.extension.applySystemBarInsets
import gd.app.musicplayer.core.common.extension.dpToPx
import gd.app.musicplayer.core.common.extension.loadMusicArtwork
import gd.app.musicplayer.core.common.extension.toDurationString
import gd.app.musicplayer.data.local.preference.SettingPreferencesDataStore
import gd.app.musicplayer.databinding.ActivityDriveModeItemBinding
import gd.app.musicplayer.databinding.FragmentDriveModeBinding
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.usecase.playback.ObservePlaybackQueueUseCase
import gd.app.musicplayer.playback.PlaybackController
import gd.app.musicplayer.ui.common.base.PlaybackQueueBottomSheetFragment
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.ui.common.playback.PlayModeViewModel
import gd.app.musicplayer.ui.player.full.PlayerViewModel
import gd.app.musicplayer.core.designsystem.view.SeekBar
import gd.app.musicplayer.ui.player.queue.PlayQueueActivity
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DriveModeFragment : ViewBindingFragment<FragmentDriveModeBinding>() {
    private var userSeeking = false
    private var pagerSyncFromState = false
    private var forwardBackwardSeconds = DEFAULT_FORWARD_BACKWARD_SECONDS
    private var currentQueue: List<Music> = emptyList()
    private val pagerAdapter = DriveModePagerAdapter()
    private val viewModel: PlayerViewModel by viewModels()
    private val playModeViewModel: PlayModeViewModel by viewModels()

    @Inject lateinit var settingPreferencesDataStore: SettingPreferencesDataStore
    @Inject lateinit var observePlaybackQueueUseCase: ObservePlaybackQueueUseCase
    @Inject lateinit var playbackController: PlaybackController

    override fun onCreateBinding(inflater: LayoutInflater): FragmentDriveModeBinding =
        FragmentDriveModeBinding.inflate(inflater)

    override fun onBindingCreated(binding: FragmentDriveModeBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        binding.root.applySystemBarInsets(binding.statusBarSpace, binding.root)
        binding.musicInfoPager.adapter = pagerAdapter
        binding.musicInfoPager.offscreenPageLimit = 2
        binding.musicInfoPager.setOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, offset: Float, offsetPixels: Int) = Unit
            override fun onPageScrollStateChanged(state: Int) = Unit

            override fun onPageSelected(position: Int) {
                if (pagerSyncFromState) return

                val selectedTrack = pagerAdapter.getItemOrNull(position) ?: return
                val actualIndex = currentQueue.indexOfFirst { it.id == selectedTrack.id }

                if (currentQueue.isNotEmpty() && actualIndex in currentQueue.indices) {
                    viewModel.playQueue(requireContext(), currentQueue, actualIndex)
                }
            }
        })

        binding.driveModeClose.setOnClickListener {
            requireActivity().finish()
        }
        binding.driveModeQueue.setOnClickListener {
            PlayQueueActivity.start(requireContext())
        }
        binding.driveModePlayPause.setOnClickListener {
            viewModel.onPrimaryPlayPauseClicked(requireContext())
        }
        binding.driveModePrevious.setOnClickListener {
            viewModel.playPrevious(requireContext())
        }
        binding.driveModeNext.setOnClickListener {
            viewModel.playNext(requireContext())
        }
        binding.driveModeBackward.setOnClickListener {
            val current = viewModel.playbackState.value.positionMs
            viewModel.seekTo(
                requireContext(),
                (current - skipDurationMs().toLong()).coerceAtLeast(0L).toInt()
            )
        }
        binding.driveModeForward.setOnClickListener {
            val state = viewModel.playbackState.value
            viewModel.seekTo(
                requireContext(),
                (state.positionMs + skipDurationMs().toLong()).coerceAtMost(state.durationMs).toInt()
            )
        }
        binding.driveModeFavorite.setOnClickListener {
            toggleFavorite()
        }
        binding.driveMode.setOnClickListener {
            playModeViewModel.cyclePlayMode()
        }

        binding.driveModeProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return

                requireBinding().driveModeCurrTime.text = progress.toLong().toDurationString()
                viewModel.seekTo(requireContext(), progress)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                userSeeking = false
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                userSeeking = true
            }
        })

        observePlayback()
        observePlayMode()
        observeSettings()
    }

    private fun observePlayback() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.playbackState,
                    viewModel.trackUiState,
                    observePlaybackQueueUseCase()
                ) { playbackState, trackState, queue ->
                    Triple(playbackState, trackState, queue)
                }.collect { (state, trackState, queue) ->
                    val binding = requireBinding()

                    currentQueue = queue
                    binding.driveModePlayPause.isSelected = state.isPlaying
                    binding.driveModeFavorite.isSelected = trackState.isFavorite
                    binding.driveModeProgress.isEnabled = state.durationMs > 0L
                    binding.driveModeProgress.setMax(state.durationMs.coerceAtLeast(1L).toInt())

                    if (!userSeeking) {
                        binding.driveModeProgress.setProgress(state.positionMs.toInt())
                    }

                    binding.driveModeCurrTime.text = state.positionMs.toDurationString()
                    binding.driveModeTotalTime.text = state.durationMs.toDurationString()

                    val displayQueue = queue.ifEmpty {
                        listOf(placeholderMusic(requireContext()))
                    }
                    pagerAdapter.submitQueue(displayQueue)

                    val targetTrackId = state.currentTrack?.id
                    val targetIndex = displayQueue.indexOfFirst { it.id == targetTrackId }
                        .takeIf { it >= 0 }
                        ?: 0

                    if (binding.musicInfoPager.currentItem != targetIndex) {
                        pagerSyncFromState = true
                        binding.musicInfoPager.setCurrentItem(targetIndex, false)
                        pagerSyncFromState = false
                    }
                }
            }
        }
    }

    private fun observePlayMode() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                playModeViewModel.uiState.collect { state ->
                    requireBinding().driveMode.setImageResource(state.iconRes)
                }
            }
        }
    }

    private fun observeSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingPreferencesDataStore.observeSettingPreferences().collect { settings ->
                    forwardBackwardSeconds = settings.normal.forwardBackwardSeconds
                    applyForwardBackwardLayout(settings.normal.showForwardBackward)
                }
            }
        }
    }

    private fun toggleFavorite() {
        if (viewModel.playbackState.value.currentTrack == null) return

        playbackController.toggleFavorite(
            context = requireContext(),
        )
    }

    private fun skipDurationMs(): Int {
        return forwardBackwardSeconds.coerceAtLeast(1) * 1000
    }

    private fun applyForwardBackwardLayout(enabled: Boolean) {
        val binding = requireBinding()

        binding.driveModeForward.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.driveModeBackward.visibility = if (enabled) View.VISIBLE else View.GONE

        if (enabled) {
            binding.driveModeBackward.setImageResource(forwardBackwardSeconds.toBackwardIconRes())
            binding.driveModeForward.setImageResource(forwardBackwardSeconds.toForwardIconRes())
            return
        } else {
            val defaultSize = requireContext().resources.getDimension(R.dimen.drive_large_icon_size)
            val basePadding = ((defaultSize - requireContext().dpToPx(40f)) / 2).toInt()
            val offset = requireContext().dpToPx(2f)
            resizeControlButton(
                view = binding.driveModePrevious,
                size = defaultSize.toInt(),
                left = basePadding - offset,
                top = basePadding,
                right = basePadding + offset,
                bottom = basePadding
            )
            resizeControlButton(
                view = binding.driveModeNext,
                size = defaultSize.toInt(),
                left = basePadding + offset,
                top = basePadding,
                right = basePadding - offset,
                bottom = basePadding
            )
            resizeControlButton(binding.driveModePlayPause, defaultSize.toInt(), 0, 0, 0, 0)
            binding.driveModePrevious.tag = "previousView"
            binding.driveModeNext.tag = "nextView"
        }
    }

    private fun resizeControlButton(
        view: ImageView,
        size: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        view.layoutParams = view.layoutParams?.apply {
            width = size
            height = size
        }
        view.setPadding(left, top, right, bottom)
    }

    private companion object {
        const val DEFAULT_FORWARD_BACKWARD_SECONDS = 15

        fun placeholderMusic(context: Context): Music = Music(
            id = -1L,
            title = context.getString(R.string.music),
            artist = context.getString(R.string.artist),
            album = "",
            albumId = "",
            playlistId = 0L,
            data = null,
            duration = 0
        )
    }

    private fun Int.toBackwardIconRes(): Int {
        return when (this) {
            5 -> R.drawable.vector_backward_5
            10 -> R.drawable.vector_backward_10
            15 -> R.drawable.vector_backward_15
            20 -> R.drawable.vector_backward_20
            30 -> R.drawable.vector_backward_30
            else -> R.drawable.vector_backward_60
        }
    }

    private fun Int.toForwardIconRes(): Int {
        return when (this) {
            5 -> R.drawable.vector_forward_5
            10 -> R.drawable.vector_forward_10
            15 -> R.drawable.vector_forward_15
            20 -> R.drawable.vector_forward_20
            30 -> R.drawable.vector_forward_30
            else -> R.drawable.vector_forward_60
        }
    }
}

private class DriveModePagerAdapter : PagerAdapter() {
    private val queue = mutableListOf<Music>()

    fun getItemOrNull(position: Int): Music? = queue.getOrNull(position)

    fun submitQueue(items: List<Music>) {
        val displayItems = items.asLoopFriendlyList()
        if (isSameQueue(displayItems)) {
            return
        }
        queue.clear()
        queue.addAll(displayItems)
        notifyDataSetChanged()
    }

    fun updateFavorite(trackId: Long, favorited: Boolean) {
        val newFavoriteId = if (favorited) 1L else 0L
        queue.indices
            .filter { queue[it].id == trackId }
            .forEach { index ->
                queue[index] = queue[index].copy(playlistId = newFavoriteId)
            }
    }

    private fun isSameQueue(items: List<Music>): Boolean {
        if (queue.size != items.size) return false
        return queue.indices.all { index ->
            val oldItem = queue[index]
            val newItem = items[index]
            oldItem.id == newItem.id &&
                oldItem.data == newItem.data &&
                oldItem.albumPicture == newItem.albumPicture &&
                oldItem.playlistId == newItem.playlistId
        }
    }

    override fun getCount(): Int = queue.size

    override fun isViewFromObject(view: View, `object`: Any): Boolean = view === `object`

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val item = queue[position]
        val binding = ActivityDriveModeItemBinding.inflate(
            LayoutInflater.from(container.context),
            container,
            false
        )
        binding.driveModeTitle.text = item.title
        binding.driveModeArtist.text = item.artist.ifBlank { binding.root.context.getString(R.string.artist) }
        binding.driveModeCover.loadMusicArtwork(item.albumArtSource())
        container.addView(binding.root)
        return binding.root
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as View)
    }

    override fun getItemPosition(`object`: Any): Int = POSITION_NONE

    private fun List<Music>.asLoopFriendlyList(): List<Music> {
        if (size != 2) return this
        // LoopViewPager behaves poorly with exactly two pages. Mirror the reference behavior and
        // duplicate them so swiping stays continuous without edge jitter.
        return listOf(this[0], this[1], this[0], this[1])
    }
}
