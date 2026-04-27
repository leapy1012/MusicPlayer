package gd.app.musicplayer.feature.player

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
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.loadMusicArtwork
import gd.app.musicplayer.databinding.FragmentQueueControlBinding
import gd.app.musicplayer.databinding.ItemMainControlPagerBinding
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.ui.common.playback.PlaybackControlViewModel
import gd.app.musicplayer.util.PreferenceUtil
import kotlinx.coroutines.launch

@AndroidEntryPoint
class QueueControlFragment : ViewBindingFragment<FragmentQueueControlBinding>() {
    private val viewModel: PlaybackControlViewModel by viewModels()
    private val pagerAdapter by lazy { QueueControlPagerAdapter { ActivityPlayQueue.start(requireContext()) } }
    private val preferenceUtil by lazy { PreferenceUtil.getInstance(requireContext()) }

    private var pagerSyncFromState = false

    override fun onCreateBinding(inflater: LayoutInflater): FragmentQueueControlBinding =
        FragmentQueueControlBinding.inflate(inflater)

    override fun onBindingCreated(
        binding: FragmentQueueControlBinding,
        savedInstanceState: Bundle?
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        binding.mainControlPager.adapter = pagerAdapter
        binding.mainControlPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) = Unit

            override fun onPageScrollStateChanged(state: Int) = Unit

            override fun onPageSelected(position: Int) {
                if (pagerSyncFromState || !pagerAdapter.isSwipeEnabled) return
                if (preferenceUtil.isSlidingSwitchEnabled()) {
                    preferenceUtil.setSlidingSwitchEnabled(false)
                    pagerAdapter.updateSlideHintEnabled(false)
                }
                val state = viewModel.playbackState.value
                if (state.queue.isNotEmpty() && position in state.queue.indices) {
                    viewModel.playQueue(requireContext(), state.queue, position)
                }
            }
        })

        binding.mainControlPlayPause.setOnClickListener {
            viewModel.togglePlayPause(requireContext())
        }
        binding.mainControlLocation.setOnClickListener {
            (parentFragmentManager.findFragmentByTag(PlaybackQueueFragment::class.java.simpleName) as? PlaybackQueueFragment)
                ?.scrollToCurrentTrack()
        }

        observePlayback()
        observePreferences()
    }

    private fun observePlayback() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.playbackState.collect { state ->
                    val binding = requireBinding()
                    binding.mainControlPlayPause.isSelected = state.isPlaying
                    binding.mainMusicProgress.setMax(state.durationMs.coerceAtLeast(1))
                    binding.mainMusicProgress.setProgress(state.positionMs.coerceAtLeast(0))

                    val queue = if (state.queue.isEmpty()) listOf(placeholderMusic()) else state.queue
                    pagerAdapter.submitQueue(
                        queue = queue,
                        swipeEnabled = preferenceUtil.getBooleanPreference(KEY_SWIPE_CHANGE_SONGS, true),
                        showSlideHint = preferenceUtil.isSlidingSwitchEnabled()
                    )
                    val targetIndex = state.currentIndex.takeIf { it in queue.indices } ?: 0
                    if (binding.mainControlPager.currentItem != targetIndex) {
                        pagerSyncFromState = true
                        binding.mainControlPager.setCurrentItem(targetIndex, false)
                        pagerSyncFromState = false
                    }
                }
            }
        }
    }

    private fun observePreferences() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                PreferenceUtil.observePreferenceChanges(KEY_SWIPE_CHANGE_SONGS, KEY_SLIDING_SWITCH).collect {
                    val enabled = preferenceUtil.getBooleanPreference(KEY_SWIPE_CHANGE_SONGS, true)
                    requireBinding().mainControlPager.isEnabled = enabled
                    pagerAdapter.updateSwipeEnabled(enabled)
                    pagerAdapter.updateSlideHintEnabled(preferenceUtil.isSlidingSwitchEnabled())
                }
            }
        }
    }

    private fun placeholderMusic(): Music = Music(
        _id = -1L,
        title = getString(android.R.string.unknownName),
        artist = getString(android.R.string.unknownName),
        album = "",
        album_id = "",
        p_id = 0L,
        data = null,
        duration = 0
    )

    private companion object {
        const val KEY_SWIPE_CHANGE_SONGS = "swipe_change_songs"
        const val KEY_SLIDING_SWITCH = "preference_sliding_switch"
    }
}

private class QueueControlPagerAdapter(
    private val onItemClick: () -> Unit
) : PagerAdapter() {
    private val realQueue = mutableListOf<Music>()
    private val displayQueue = mutableListOf<Music>()

    var isSwipeEnabled: Boolean = true
        private set

    private var showSlideHint = true

    fun submitQueue(queue: List<Music>, swipeEnabled: Boolean, showSlideHint: Boolean) {
        val loopFriendlyQueue = if (queue.size == 2) listOf(queue[0], queue[1], queue[0], queue[1]) else queue
        realQueue.clear()
        realQueue.addAll(queue)
        displayQueue.clear()
        displayQueue.addAll(loopFriendlyQueue)
        isSwipeEnabled = swipeEnabled
        this.showSlideHint = showSlideHint
        notifyDataSetChanged()
    }

    fun updateSwipeEnabled(enabled: Boolean) {
        if (isSwipeEnabled == enabled) return
        isSwipeEnabled = enabled
        notifyDataSetChanged()
    }

    fun updateSlideHintEnabled(enabled: Boolean) {
        if (showSlideHint == enabled) return
        showSlideHint = enabled
        notifyDataSetChanged()
    }

    override fun getCount(): Int = displayQueue.size

    override fun isViewFromObject(view: View, `object`: Any): Boolean = view === `object`

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val binding = ItemMainControlPagerBinding.inflate(
            LayoutInflater.from(container.context),
            container,
            false
        )
        val music = displayQueue[position]
        binding.itemMainControlTitle.text = music.title
        binding.itemMainControlArtist.text =
            if (isSwipeEnabled && showSlideHint && realQueue.size > 1) {
                binding.root.context.getString(R.string.sliding_to_swtich)
            } else {
                music.artist.ifBlank { binding.root.context.getString(android.R.string.unknownName) }
            }
        music.loadMusicArtwork(binding.itemMainControlAlbum)
        binding.root.setOnClickListener { onItemClick() }
        container.addView(binding.root)
        return binding.root
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as View)
    }

    override fun getItemPosition(`object`: Any): Int = POSITION_NONE
}
