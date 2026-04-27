package gd.app.musicplayer.ui.common.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
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
import gd.app.musicplayer.databinding.ItemMainControlPagerBinding
import gd.app.musicplayer.databinding.MainBottomControlPanelBinding
import gd.app.musicplayer.ui.common.playback.PlaybackControlViewModel
import gd.app.musicplayer.feature.player.ActivityPlayQueue
import gd.app.musicplayer.util.PreferenceUtil
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MiniPlayerFragment : ViewBindingFragment<MainBottomControlPanelBinding>() {
    private val viewModel: PlaybackControlViewModel by viewModels()
    private val pagerAdapter by lazy { MiniPlayerPagerAdapter(::openPlayer) }
    private val preferenceUtil by lazy { PreferenceUtil.getInstance(requireContext()) }

    private var pagerSyncFromState = false

    private fun setupInsets(binding: MainBottomControlPanelBinding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            view.updatePadding(
                bottom = navBottom
            )

            insets
        }

        ViewCompat.requestApplyInsets(binding.root)
    }

    override fun onCreateBinding(inflater: LayoutInflater): MainBottomControlPanelBinding =
        MainBottomControlPanelBinding.inflate(inflater)

    override fun onBindingCreated(
        binding: MainBottomControlPanelBinding,
        savedInstanceState: Bundle?
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        setupInsets(binding)

        binding.mainMusicProgress.isEnabled = false
        binding.mainControlPager.adapter = pagerAdapter
        binding.mainControlPager.setOnPageChangeListener(object : ViewPager.OnPageChangeListener {
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
            val state = viewModel.playbackState.value
            if (state.hasTrack) {
                viewModel.togglePlayPause(requireContext())
            } else {
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.playAllTracks(requireContext())
                }
            }
        }
        binding.mainControlNext.setOnClickListener {
            viewModel.playNext(requireContext())
        }
        binding.mainControlList.setOnClickListener {
            ActivityPlayQueue.startQueue(requireContext())
        }

        observePlaybackState()
        observePreferences()
    }

    private fun observePlaybackState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.playbackState.collect { state ->
                    val binding = requireBinding()
                    binding.mainControlPlayPause.isSelected = state.isPlaying
                    binding.mainMusicProgress.setMax(state.durationMs.coerceAtLeast(1))
                    binding.mainMusicProgress.setProgress(state.positionMs.coerceAtLeast(0))

                    val queue = state.queue.ifEmpty { listOf(placeholderMusic()) }
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

    private fun openPlayer() {
        ActivityPlayQueue.start(requireContext())
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

private class MiniPlayerPagerAdapter(
    private val onItemClick: () -> Unit
) : PagerAdapter() {
    private val realQueue = mutableListOf<Music>()
    private val displayQueue = mutableListOf<Music>()

    var isSwipeEnabled: Boolean = true
        private set

    private var showSlideHint = true

    fun submitQueue(queue: List<Music>, swipeEnabled: Boolean, showSlideHint: Boolean) {
        val loopFriendlyQueue = queue.asLoopFriendlyList()
        val queueChanged = !hasSameItems(realQueue, queue) || !hasSameItems(displayQueue, loopFriendlyQueue)
        realQueue.clear()
        realQueue.addAll(queue)
        displayQueue.clear()
        displayQueue.addAll(loopFriendlyQueue)

        if (isSwipeEnabled != swipeEnabled || this.showSlideHint != showSlideHint || queueChanged) {
            isSwipeEnabled = swipeEnabled
            this.showSlideHint = showSlideHint
            notifyDataSetChanged()
        }
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
        bind(binding, displayQueue[position])
        container.addView(binding.root)
        return binding.root
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as View)
    }

    override fun getItemPosition(`object`: Any): Int = POSITION_NONE

    private fun bind(binding: ItemMainControlPagerBinding, music: Music) {
        binding.itemMainControlTitle.setHorizontalScrollable(false)
        binding.itemMainControlArtist.setHorizontalScrollable(false)
        binding.itemMainControlTitle.text = music.title
        binding.itemMainControlAlbum.resetStateIfMusicChanged(music)
        music.loadMusicArtwork(binding.itemMainControlAlbum, R.drawable.notify_default_album_circle)
        binding.itemMainControlArtist.text = if (shouldShowSwipeHint()) {
            binding.root.context.getString(R.string.sliding_to_swtich)
        } else {
            music.artist.ifBlank { binding.root.context.getString(android.R.string.unknownName) }
        }
        binding.root.setOnClickListener { onItemClick() }
    }

    private fun shouldShowSwipeHint(): Boolean = isSwipeEnabled && showSlideHint && realQueue.size > 1

    private fun hasSameItems(old: List<Music>, new: List<Music>): Boolean {
        if (old.size != new.size) return false
        return old.indices.all { index ->
            old[index]._id == new[index]._id &&
                old[index].data == new[index].data &&
                old[index].title == new[index].title &&
                old[index].artist == new[index].artist
        }
    }

    private fun List<Music>.asLoopFriendlyList(): List<Music> {
        if (size != 2) return this
        return listOf(this[0], this[1], this[0], this[1])
    }
}
