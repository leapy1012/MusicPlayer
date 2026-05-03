package gd.app.musicplayer.ui.feature.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import dagger.hilt.android.AndroidEntryPoint
import gd.app.lib.model.visualizer.AudioVisualizerManager
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.applySystemBarInsets
import gd.app.musicplayer.core.extension.isFavorite
import gd.app.musicplayer.core.extension.isLandscape
import gd.app.musicplayer.core.extension.loadMusicArtwork
import gd.app.musicplayer.core.extension.navigateBack
import gd.app.musicplayer.core.ui.view.SeekBar
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.databinding.FragmentMusicPlayVisualizerBinding
import gd.app.musicplayer.databinding.FragmentPlayContentBinding
import gd.app.musicplayer.databinding.MusicPlayFragmentInfoBinding
import gd.app.musicplayer.databinding.MusicPlayFragmentLrcBinding
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.ui.common.playback.PlayModeViewModel
import gd.app.musicplayer.ui.feature.equalizer.EffectGroupActivity
import gd.app.musicplayer.ui.feature.equalizer.EqualizerActivity
import gd.app.musicplayer.ui.feature.library.AlbumMusicActivity
import gd.app.musicplayer.ui.feature.library.CurrentTrackOptionsDialog
import gd.app.musicplayer.ui.feature.lyrics.LyricAdjustDialogFragment
import gd.app.musicplayer.ui.feature.lyrics.LyricSearchDialogFragment
import gd.app.musicplayer.ui.feature.lyrics.LyricSettingsDialogFragment
import gd.app.musicplayer.ui.feature.lyrics.a
import gd.app.musicplayer.ui.feature.lyrics.hasTimedLyrics
import gd.app.musicplayer.ui.feature.lyrics.setLyricText
import gd.app.musicplayer.util.LyricsLoader
import gd.app.musicplayer.util.PreferenceUtil
import gd.app.musicplayer.util.TrackLyricsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MusicPlayerFragment :
    ViewBindingFragment<FragmentPlayContentBinding>(),
    View.OnClickListener,
    SeekBar.OnSeekBarChangeListener,
    ViewPager.OnPageChangeListener {

    private val viewModel: MusicPlayViewModel by viewModels()
    private val playModeViewModel: PlayModeViewModel by viewModels()

    private var visualizerBinding: FragmentMusicPlayVisualizerBinding? = null
    private var infoBinding: MusicPlayFragmentInfoBinding? = null
    private var lyricBinding: MusicPlayFragmentLrcBinding? = null

    private var pagerIndex = PAGE_ALBUM
    private var currentTrack: Music? = null
    private var userSeeking = false
    private var favoriteOverride: Boolean? = null
    private var currentAudioSessionId: Int = -1
    private var isPlaybackActive: Boolean = false
    private var hasVisualizerPermission = false
    private var lyricLoadJob: Job? = null
    private var currentLyricSource: String? = null

    private val visualizerPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasVisualizerPermission = granted
            updateVisualizerState()
        }

    override fun onCreateBinding(inflater: LayoutInflater): FragmentPlayContentBinding {
        return FragmentPlayContentBinding.inflate(inflater)
    }

    override fun onBindingCreated(
        binding: FragmentPlayContentBinding,
        savedInstanceState: Bundle?
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        pagerIndex = savedInstanceState?.getInt(KEY_PAGER_INDEX, PAGE_ALBUM) ?: PAGE_ALBUM
        hasVisualizerPermission = hasVisualizerPermission()

        setupUi(binding)
        setupFragmentResults()
        observePlayback()
        observeLyricPreferenceChanges()
    }

    private fun setupUi(binding: FragmentPlayContentBinding) {
        binding.root.applySystemBarInsets(binding.statusBarSpace)

        setupToolbar(binding)
        setupPager(binding)
        setupControls(binding)

        applyLyricPreferences()
        updateForwardBackwardVisibility()
        observePlayMode()
    }

    private fun setupToolbar(binding: FragmentPlayContentBinding) {
        binding.toolbar.navigateBack(this)
        binding.toolbar.inflateMenu(R.menu.menu_activity_music_play)
        binding.toolbar.setOnMenuItemClickListener { item: MenuItem ->
            if (item.itemId == R.id.menu_list_menu) {
                PlayQueueActivity.startQueue(requireContext())
            }
            true
        }
    }

    private fun setupControls(binding: FragmentPlayContentBinding) = with(binding) {

        musicPlayProgress.apply {
            musicPlayProgress.setThumbColor(
                ContextCompat.getColor(requireContext(), android.R.color.white)
            )
            musicPlayProgress.setOnSeekBarChangeListener(this@MusicPlayerFragment)
            musicPlayTempo.setOnClickListener(this@MusicPlayerFragment)
        }

        musicPlayController.apply {
            controlPlayPause.setOnClickListener(this@MusicPlayerFragment)
            controlMode.setOnClickListener(this@MusicPlayerFragment)
            controlBackward.setOnClickListener(this@MusicPlayerFragment)
            controlPrevious.setOnClickListener(this@MusicPlayerFragment)
            controlNext.setOnClickListener(this@MusicPlayerFragment)
            controlForward.setOnClickListener(this@MusicPlayerFragment)
            controlEqualizer.setOnClickListener(this@MusicPlayerFragment)
        }



        musicPlayFavourite.setOnClickListener(this@MusicPlayerFragment)
        musicPlaySoundEffect.setOnClickListener(this@MusicPlayerFragment)
        musicPlayLyricSearch.setOnClickListener(this@MusicPlayerFragment)
        musicLyricSetting.setOnClickListener(this@MusicPlayerFragment)
        musicPlayMore.setOnClickListener(this@MusicPlayerFragment)
        musicPlayContentTitle.musicPlayArtist.setOnClickListener(this@MusicPlayerFragment)
    }

    private fun setupPager(binding: FragmentPlayContentBinding) {
        val inflater = LayoutInflater.from(requireContext())

        val visualizerPageBinding = FragmentMusicPlayVisualizerBinding.inflate(
            inflater,
            binding.musicPlayPager,
            false
        )

        val infoPageBinding = MusicPlayFragmentInfoBinding.inflate(
            inflater,
            binding.musicPlayPager,
            false
        )

        val lyricPageBinding = MusicPlayFragmentLrcBinding.inflate(
            inflater,
            binding.musicPlayPager,
            false
        )

        visualizerBinding = visualizerPageBinding
        infoBinding = infoPageBinding
        lyricBinding = lyricPageBinding

        setupAlbumPage(infoPageBinding)
        setupLyricPage(lyricPageBinding)

        val pages = listOf(
            visualizerPageBinding.root,
            infoPageBinding.root,
            lyricPageBinding.root
        )

        binding.musicPlayPager.adapter = object : PagerAdapter() {
            override fun getCount(): Int = pages.size

            override fun isViewFromObject(view: View, obj: Any): Boolean {
                return view === obj
            }

            override fun instantiateItem(container: ViewGroup, position: Int): Any {
                val page = pages[position]
                container.addView(page)
                return page
            }

            override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
                container.removeView(obj as View)
            }
        }

        binding.musicPlayPagerIndicator.setViewPager(binding.musicPlayPager)
        binding.musicPlayPagerIndicator.setOnPageChangeListener(this)

        binding.musicPlayPager.currentItem = pagerIndex
        onPageSelected(pagerIndex)
    }

    private fun setupAlbumPage(binding: MusicPlayFragmentInfoBinding) = with(binding) {
        layoutMusicPlayAlbumParent.setSquare { widthMeasureSpec, heightMeasureSpec ->
            val availableSize = minOf(widthMeasureSpec, heightMeasureSpec)

            val targetSize = if (requireContext().isLandscape()) {
                availableSize
            } else {
                availableSize * 8 / 9
            }

            val exactSpec = View.MeasureSpec.makeMeasureSpec(
                targetSize,
                View.MeasureSpec.EXACTLY
            )

            intArrayOf(exactSpec, exactSpec)
        }
    }

    private fun setupLyricPage(binding: MusicPlayFragmentLrcBinding) = with(binding) {
        musicPlayLrcSearch.setOnClickListener(this@MusicPlayerFragment)

        musicPlayLrc.setOnClickListener {
            if (musicPlayLrc.a()) {
                (activity as? MusicPlayActivity)?.showLyrics()
            }
        }
    }

    private fun setupFragmentResults() {
        childFragmentManager.setFragmentResultListener(
            LyricSettingsDialogFragment.RESULT_KEY,
            viewLifecycleOwner
        ) { _, _ ->
            applyLyricPreferences()
            updateLyricAutoScroll()
        }

        childFragmentManager.setFragmentResultListener(
            LyricAdjustDialogFragment.RESULT_KEY,
            viewLifecycleOwner
        ) { _, _ ->
            val lyricView = lyricBinding?.musicPlayLrc ?: return@setFragmentResultListener
            val track = currentTrack ?: return@setFragmentResultListener

            lyricView.setTimeOffset(
                TrackLyricsStore.from(requireContext()).getTrackLyricOffset(track.id)
            )
            maybeLoadLyrics(track)
        }

        childFragmentManager.setFragmentResultListener(
            LyricSearchDialogFragment.RESULT_KEY,
            viewLifecycleOwner
        ) { _, _ ->
            currentLyricSource = null
            currentTrack?.let(::maybeLoadLyrics)
        }
    }

    private fun observePlayback() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.playbackState.collect { state ->
                    val binding = binding ?: return@collect
                    val lyricPageBinding = lyricBinding ?: return@collect
                    val infoPageBinding = infoBinding ?: return@collect

                    currentTrack = state.currentMusic
                    val track = state.currentMusic

                    if (track == null) {
                        renderEmptyState(
                            binding = binding,
                            lyricBinding = lyricPageBinding
                        )
                        return@collect
                    }

                    currentAudioSessionId = -1
                    isPlaybackActive = state.isPlaying

                    renderTrackState(
                        binding = binding,
                        infoBinding = infoPageBinding,
                        lyricBinding = lyricPageBinding,
                        track = track,
                        positionMs = state.positionMs,
                        durationMs = state.durationMs,
                        isPlaying = state.isPlaying
                    )

                    maybeLoadLyrics(track)
                    updateLyricAutoScroll()
                    updateVisualizerState()
                }
            }
        }
    }

    private fun renderEmptyState(
        binding: FragmentPlayContentBinding,
        lyricBinding: MusicPlayFragmentLrcBinding
    ) = with(binding) {
        lyricLoadJob?.cancel()
        currentLyricSource = null
        currentAudioSessionId = -1
        isPlaybackActive = false
        favoriteOverride = null

        musicPlayContentTitle.apply {
            musicPlayName.text = getString(R.string.music)
            musicPlayArtist.text = getString(R.string.artist)
        }

        musicPlayProgress.apply {
            musicPlayCurrTime.text = viewModel.formatTime(0)
            musicPlayTotalTime.text = viewModel.formatTime(0)

            musicPlayProgress.setMax(1)
            musicPlayProgress.setProgress(0)
        }

        musicPlayController.apply {
            controlPlayPause.isSelected = false
        }


        musicPlayFavourite.isSelected = false

        lyricBinding.musicPlayLrc.setLyricText(null)
        lyricBinding.root.displayedChild = LYRIC_PAGE_LOADING

        updateVisualizerState()
    }

    private fun renderTrackState(
        binding: FragmentPlayContentBinding,
        infoBinding: MusicPlayFragmentInfoBinding,
        lyricBinding: MusicPlayFragmentLrcBinding,
        track: Music,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean
    ) = with(binding) {

        musicPlayContentTitle.apply {
            musicPlayName.text = track.title
            musicPlayArtist.text = track.artist.ifBlank { getString(R.string.artist) }
        }

        musicPlayController.apply {
            controlPlayPause.isSelected = isPlaying
        }

        musicPlayProgress.apply {
            val durationInt = durationMs.coerceAtLeast(1L).toInt()
            val positionInt = positionMs.coerceAtLeast(0L).toInt()
            musicPlayTotalTime.text = viewModel.formatTime(durationInt)
            musicPlayCurrTime.text = viewModel.formatTime(positionInt)
            musicPlayProgress.setMax(durationInt)

            if (!userSeeking) {
                musicPlayProgress.setProgress(positionInt)
            }
        }



        lyricBinding.musicPlayLrc.setCurrentTime(positionMs.toLong())

        val isFavorite = favoriteOverride ?: track.isFavorite()
        musicPlayFavourite.isSelected = isFavorite

        track.loadMusicArtwork(infoBinding.musicPlayAlbum)
    }

    private fun observeLyricPreferenceChanges() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                PreferenceUtil.getInstance(requireContext())
                    .observePreferenceChanges(
                        "preference_lyric_color",
                        "preference_lyric_text_size",
                        "lyric_auto_scroll",
                        "lyric_align",
                        "lyric_style"
                    )
                    .collect {
                        applyLyricPreferences()
                        updateLyricAutoScroll()
                    }
            }
        }
    }

    private fun updateVisualizerState() {
        visualizerBinding ?: return

        val shouldUseVisualizer =
            isResumed &&
                    pagerIndex == PAGE_VISUALIZER &&
                    isPlaybackActive &&
                    currentAudioSessionId > 0

        if (shouldUseVisualizer && !hasVisualizerPermission) {
            requestVisualizerPermission()
        }

        AudioVisualizerManager.setAudioSessionId(currentAudioSessionId)
        AudioVisualizerManager.setPlaybackActive(isPlaybackActive)
        AudioVisualizerManager.setUserEnabled(shouldUseVisualizer)
        AudioVisualizerManager.setUseRealAudioData(hasVisualizerPermission)
    }

    private fun hasVisualizerPermission(): Boolean {
        val context = context ?: return false

        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestVisualizerPermission() {
        if (!isAdded || hasVisualizerPermission) return

        visualizerPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun applyLyricPreferences() {
        val lyricView = lyricBinding?.musicPlayLrc ?: return
        if (!isAdded) return

        val prefs = PreferenceUtil.getInstance(requireContext())

        lyricView.setCurrentTextColor(prefs.getLyricColor())
        lyricView.setTextSize(prefs.getLyricTextSize())
        lyricView.setTextAlign(prefs.getLyricAlign())
        lyricView.setTextTypeface(prefs.getLyricStyle())
    }

    private fun updateLyricAutoScroll() {
        val lyricView = lyricBinding?.musicPlayLrc ?: return
        if (!isAdded) return

        val prefs = PreferenceUtil.getInstance(requireContext())

        lyricView.setAutoScroll(
            pagerIndex == PAGE_LYRIC &&
                    isResumed &&
                    isPlaybackActive &&
                    lyricView.hasTimedLyrics() &&
                    prefs.isLyricAutoScrollEnabled()
        )
    }

    private fun maybeLoadLyrics(track: Music) {
        val lyricPageBinding = lyricBinding ?: return
        val lyricView = lyricPageBinding.musicPlayLrc
        val source = track.data

        if (source == currentLyricSource) {
            when {
                lyricView.a() -> lyricPageBinding.root.displayedChild = LYRIC_PAGE_CONTENT
                source.isNullOrBlank() -> lyricPageBinding.root.displayedChild = LYRIC_PAGE_EMPTY
            }
            return
        }

        currentLyricSource = source
        lyricLoadJob?.cancel()

        lyricView.setTimeOffset(
            TrackLyricsStore.from(requireContext()).getTrackLyricOffset(track.id)
        )

        if (source.isNullOrBlank()) {
            lyricView.setLyricText(null)
            lyricPageBinding.root.displayedChild = LYRIC_PAGE_EMPTY
            return
        }

        lyricView.setLyricText(null)
        lyricPageBinding.root.displayedChild = LYRIC_PAGE_LOADING

        lyricLoadJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = LyricsLoader.load(requireContext(), track.id, source)

            if (!isAdded || currentLyricSource != source) return@launch

            lyricView.setTimeOffset(
                TrackLyricsStore.from(requireContext()).getTrackLyricOffset(track.id)
            )

            if (!result.hasLyrics) {
                lyricPageBinding.root.displayedChild = LYRIC_PAGE_EMPTY
            } else {
                lyricView.setLyricText(result.text)
                lyricPageBinding.root.displayedChild = LYRIC_PAGE_CONTENT
            }

            applyLyricPreferences()
            updateLyricAutoScroll()
        }
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.control_play_pause -> onPlayPauseClicked()
            R.id.control_previous -> viewModel.playPrevious(requireContext())
            R.id.control_next -> viewModel.playNext(requireContext())
            R.id.control_backward -> seekBy(-seekIncrementMs())
            R.id.control_forward -> seekBy(seekIncrementMs())
            R.id.control_equalizer -> EqualizerActivity.start(requireContext())
            R.id.music_play_sound_effect -> EffectGroupActivity.start(requireContext())
            R.id.control_mode -> playModeViewModel.cyclePlayMode()
            R.id.music_play_more -> openTrackOptions()
            R.id.music_play_lrc_search,
            R.id.music_play_lyric_search -> openLyricSearch()
            R.id.music_lyric_setting -> openLyricSettings()
            R.id.music_play_tempo -> TempoDialogFragment.show(childFragmentManager)
            R.id.music_play_favourite -> toggleFavorite()
            R.id.music_play_artist -> openArtist()
        }
    }

    private fun onPlayPauseClicked() {
        val state = viewModel.playbackState.value

        if (state.queue.isEmpty()) {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.playAllTracks(requireContext())
            }
        } else {
            viewModel.togglePlayPause(requireContext())
        }
    }

    private fun openTrackOptions() {
        val track = currentTrack ?: return

        CurrentTrackOptionsDialog.newInstance(track)
            .show(parentFragmentManager, CurrentTrackOptionsDialog::class.java.simpleName)
    }

    private fun seekBy(deltaMs: Int) {
        val state = viewModel.playbackState.value
        val target = (state.positionMs + deltaMs.toLong()).coerceIn(0L, state.durationMs).toInt()

        viewModel.seekTo(requireContext(), target)
    }

    private fun openLyricSearch() {
        val track = currentTrack ?: run {
            ToastUtil.show(requireContext(), getString(R.string.list_is_empty))
            return
        }

        LyricSearchDialogFragment.newInstance(
            track.id,
            track.title,
            track.artist,
            track.data
        ).show(childFragmentManager, LyricSearchDialogFragment.TAG)
    }

    private fun openLyricSettings() {
        val track = currentTrack ?: run {
            ToastUtil.show(requireContext(), getString(R.string.list_is_empty))
            return
        }

        val lyricView = lyricBinding?.musicPlayLrc ?: return

        LyricSettingsDialogFragment.newInstance(
            track.id,
            lyricView.hasTimedLyrics()
        ).show(childFragmentManager, LyricSettingsDialogFragment.TAG)
    }

    private fun seekIncrementMs(): Int {
        val seconds = PreferenceUtil.getInstance(requireContext())
            .getIntPreference("time_forward_backward", 15)

        return seconds.coerceIn(5, 60) * 1_000
    }

    private fun updateForwardBackwardVisibility() {
        val binding = binding ?: return
        val prefs = PreferenceUtil.getInstance(requireContext())

        val enabled = prefs.getBooleanPreference("show_forward_backward", false)
        val seconds = prefs.getIntPreference("time_forward_backward", 15)

        binding.musicPlayController.apply {
            controlBackward.isVisible = enabled
            controlForward.isVisible = enabled
        }

        if (!enabled) return

        binding.musicPlayController.apply {
            controlBackward.setImageResource(seconds.toBackwardIconRes())
            controlForward.setImageResource(seconds.toForwardIconRes())
        }


    }

    private fun observePlayMode() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                playModeViewModel.uiState.collect { state ->
                    binding?.musicPlayController?.controlMode?.setImageResource(state.iconRes)
                }
            }
        }
    }

    private fun toggleFavorite() {
        val binding = binding ?: return
        val track = currentTrack ?: return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            favoriteOverride = viewModel.toggleFavorite(track.id)

            launch(Dispatchers.Main) {
                binding.musicPlayFavourite.isSelected = favoriteOverride == true
            }
        }
    }

    private fun openArtist() {
        val track = currentTrack ?: return
        val artistName = track.artist.takeIf { it.isNotBlank() } ?: return

        AlbumMusicActivity.start(
            requireContext(),
            MusicSet.Artist(
                id = MusicSet.ARTISTS,
                name = artistName,
                musicCount = 0,
                albumCount = 0,
                albumArt = null
            )
        )
    }

    override fun onProgressChanged(
        seekBar: SeekBar,
        progress: Int,
        fromUser: Boolean
    ) {
        if (!fromUser) return

        binding?.musicPlayProgress?.musicPlayCurrTime?.text = viewModel.formatTime(progress)
        viewModel.seekTo(requireContext(), progress)
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        userSeeking = false
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        userSeeking = true
    }

    override fun onPageSelected(position: Int) {
        val binding = binding ?: return

        pagerIndex = position

        val lyricPage = position == PAGE_LYRIC

        binding.musicPlaySoundEffect.isVisible = !lyricPage
        binding.musicPlayLyricSearch.isVisible = lyricPage
        binding.musicLyricSetting.isVisible = lyricPage

        updateLyricAutoScroll()
        updateVisualizerState()
    }

    override fun onPageScrolled(
        position: Int,
        positionOffset: Float,
        positionOffsetPixels: Int
    ) = Unit

    override fun onPageScrollStateChanged(state: Int) = Unit

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PAGER_INDEX, pagerIndex)
    }

    override fun onResume() {
        super.onResume()

        hasVisualizerPermission = hasVisualizerPermission()

        applyLyricPreferences()
        updateForwardBackwardVisibility()
        updateLyricAutoScroll()
        updateVisualizerState()
    }

    override fun onPause() {
        lyricBinding?.musicPlayLrc?.setAutoScroll(false)
        AudioVisualizerManager.setUserEnabled(false)

        super.onPause()
    }

    override fun onDestroyView() {
        lyricLoadJob?.cancel()
        lyricLoadJob = null

        visualizerBinding = null
        infoBinding = null
        lyricBinding = null

        super.onDestroyView()
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

    private companion object {
        private const val KEY_PAGER_INDEX = "pager_index"

        private const val PAGE_VISUALIZER = 0
        private const val PAGE_ALBUM = 1
        private const val PAGE_LYRIC = 2

        private const val LYRIC_PAGE_LOADING = 0
        private const val LYRIC_PAGE_CONTENT = 1
        private const val LYRIC_PAGE_EMPTY = 3
    }
}

