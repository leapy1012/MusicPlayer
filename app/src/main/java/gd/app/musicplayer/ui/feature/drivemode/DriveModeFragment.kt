package gd.app.musicplayer.ui.feature.drivemode

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.appDependencies
import gd.app.musicplayer.core.extension.isFavorite
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.core.extension.loadMusicArtwork
import gd.app.musicplayer.core.extension.toDurationString
import gd.app.musicplayer.databinding.ActivityDriveModeItemBinding
import gd.app.musicplayer.databinding.FragmentDriveModeBinding
import gd.app.musicplayer.ui.common.base.PlaybackQueueBottomSheetFragment
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.ui.common.playback.PlayModeViewModel
import gd.app.musicplayer.ui.player.PlayerViewModel
import gd.app.musicplayer.core.ui.view.SeekBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DriveModeFragment : ViewBindingFragment<FragmentDriveModeBinding>() {
    private var userSeeking = false
    private var pagerSyncFromState = false
    private val pagerAdapter = DriveModePagerAdapter()
    private val viewModel: PlayerViewModel by viewModels()
    private val playModeViewModel: PlayModeViewModel by viewModels()
    private val preferenceUtil by lazy { requireContext().appDependencies.preferenceUtil }
    private val toggleFavoriteTrack by lazy {
        requireContext().appDependencies.toggleFavoriteTrackUseCase
    }

    override fun onCreateBinding(inflater: LayoutInflater): FragmentDriveModeBinding =
        FragmentDriveModeBinding.inflate(inflater)

    override fun onBindingCreated(binding: FragmentDriveModeBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        binding.musicInfoPager.adapter = pagerAdapter
        binding.musicInfoPager.offscreenPageLimit = 2
        binding.musicInfoPager.setOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, offset: Float, offsetPixels: Int) = Unit
            override fun onPageScrollStateChanged(state: Int) = Unit

            override fun onPageSelected(position: Int) {
//                if (pagerSyncFromState) return
//                val state = viewModel.playbackState.value
//                if (state.queue.isNotEmpty() && position in state.queue.indices) {
//                    viewModel.playQueue(requireContext(), state.queue, position)
//                }
            }
        })

        binding.driveModeClose.setOnClickListener {
            requireActivity().finish()
        }
        binding.driveModeQueue.setOnClickListener {
            PlaybackQueueBottomSheetFragment.show(parentFragmentManager)
        }
        binding.driveModePlayPause.setOnClickListener {
//            val state = viewModel.playbackState.value
//            if (state.queue.isEmpty()) {
//                viewLifecycleOwner.lifecycleScope.launch {
//                    viewModel.playAllTracks(requireContext())
//                }
//            } else {
//                viewModel.togglePlayPause(requireContext())
//            }
        }
        binding.driveModePrevious.setOnClickListener {
            viewModel.playPrevious(requireContext())
        }
        binding.driveModeNext.setOnClickListener {
            viewModel.playNext(requireContext())
        }
        binding.driveModeBackward.setOnClickListener {
//            val current = viewModel.playbackState.value.positionMs
//            viewModel.seekTo(
//                requireContext(),
//                (current - skipDurationMs().toLong()).coerceAtLeast(0L).toInt()
//            )
        }
        binding.driveModeForward.setOnClickListener {
//            val state = viewModel.playbackState.value
//            viewModel.seekTo(
//                requireContext(),
//                (state.positionMs + skipDurationMs().toLong()).coerceAtMost(state.durationMs).toInt()
//            )
        }
        binding.driveModeFavorite.setOnClickListener {
            toggleFavorite()
        }
        binding.driveMode.setOnClickListener {
            playModeViewModel.cyclePlayMode()
        }

        binding.driveModeProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                userSeeking = false
                viewModel.seekTo(requireContext(), seekBar.getProgress())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                userSeeking = true
            }
        })

        observePlayback()
        observePlayMode()
    }

    private fun observePlayback() {
//        viewLifecycleOwner.lifecycleScope.launch {
//            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
//                viewModel.playbackState.collect { state ->
//                    val binding = requireBinding()
//
//                    binding.driveModePlayPause.isSelected = state.isPlaying
//                    binding.driveModeFavorite.isSelected = state.currentTrack?.isFavorite() == true
//                    binding.driveModeProgress.isEnabled = state.durationMs > 0
//                    binding.driveModeProgress.setMax(state.durationMs.coerceAtLeast(1L).toInt())
//                    if (!userSeeking) {
//                        binding.driveModeProgress.setProgress(state.positionMs.toInt())
//                    }
//                    binding.driveModeCurrTime.text = state.positionMs.toDurationString()
//                    binding.driveModeTotalTime.text = state.durationMs.toDurationString()
//
//                    val queue = state.queue.ifEmpty { listOf(placeholderMusic(requireContext())) }
//                    pagerAdapter.submitQueue(queue)
//                    val targetIndex = state.currentIndex.takeIf { it in queue.indices } ?: 0
//                    if (binding.musicInfoPager.currentItem != targetIndex) {
//                        pagerSyncFromState = true
//                        binding.musicInfoPager.setCurrentItem(targetIndex, false)
//                        pagerSyncFromState = false
//                    }
//                }
//            }
//        }
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

    private fun toggleFavorite() {
//        val track = viewModel.playbackState.value.currentTrack ?: return
//        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
//            val favorited = toggleFavoriteTrack(track.id)
//            launch(Dispatchers.Main) {
//                requireBinding().driveModeFavorite.isSelected = favorited
//                pagerAdapter.updateFavorite(track.id, favorited)
//            }
//        }
    }

    private fun skipDurationMs(): Int {
        val seconds = preferenceUtil.getIntPreference(KEY_FORWARD_BACKWARD_SECONDS, 15)
        return seconds.coerceAtLeast(1) * 1000
    }

    private companion object {
        const val KEY_FORWARD_BACKWARD_SECONDS = "forward_backward_seconds"

        fun placeholderMusic(context: android.content.Context): Music = Music(
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
}

private class DriveModePagerAdapter : PagerAdapter() {
    private val queue = mutableListOf<Music>()

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
        item.loadMusicArtwork(binding.driveModeCover)
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

