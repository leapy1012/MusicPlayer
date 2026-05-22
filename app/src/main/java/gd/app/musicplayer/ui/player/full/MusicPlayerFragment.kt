package gd.app.musicplayer.ui.player.full

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.bumptech.glide.Glide
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.data.local.preference.LyricSettingPreferenceStore
import gd.app.musicplayer.data.local.preference.LyricsSettingPreference
import gd.app.musicplayer.data.local.preference.SettingPreferencesDataStore
import gd.app.lib.model.visualizer.AudioVisualizerManager
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applySystemBarInsets
import gd.app.musicplayer.core.common.extension.isLandscape
import gd.app.musicplayer.core.common.extension.navigateBack
import gd.app.musicplayer.core.common.extension.toDurationString
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.core.designsystem.view.SeekBar
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.databinding.FragmentMusicPlayVisualizerBinding
import gd.app.musicplayer.databinding.FragmentPlayContentBinding
import gd.app.musicplayer.databinding.MusicPlayFragmentInfoBinding
import gd.app.musicplayer.databinding.MusicPlayFragmentLrcBinding
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.ui.common.playback.PlayModeViewModel
import gd.app.musicplayer.ui.equalizer.EffectGroupActivity
import gd.app.musicplayer.ui.equalizer.EqualizerActivity
import gd.app.musicplayer.ui.library.albums.AlbumMusicActivity
import gd.app.musicplayer.ui.library.options.CurrentTrackOptionsDialog
import gd.app.musicplayer.ui.lyrics.LyricAdjustDialogFragment
import gd.app.musicplayer.ui.lyrics.LyricSearchDialogFragment
import gd.app.musicplayer.ui.lyrics.LyricSettingsDialogFragment
import gd.app.musicplayer.ui.lyrics.a
import gd.app.musicplayer.ui.lyrics.hasTimedLyrics
import gd.app.musicplayer.ui.lyrics.setLyricText
import gd.app.musicplayer.ui.player.queue.PlayQueueActivity
import gd.app.musicplayer.ui.player.TempoDialogFragment
import gd.app.musicplayer.util.LyricsLoader

import gd.app.musicplayer.util.TrackLyricsStore
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MusicPlayerFragment :
    ViewBindingFragment<FragmentPlayContentBinding>(),
    View.OnClickListener,
    SeekBar.OnSeekBarChangeListener,
    ViewPager.OnPageChangeListener {

    @Inject
    lateinit var lyricSettingPreferenceStore: LyricSettingPreferenceStore
    @Inject
    lateinit var settingPreferencesDataStore: SettingPreferencesDataStore

    private val playerViewModel: PlayerViewModel by activityViewModels()
    private val playModeViewModel: PlayModeViewModel by viewModels()

    private var visualizerBinding: FragmentMusicPlayVisualizerBinding? = null
    private var infoBinding: MusicPlayFragmentInfoBinding? = null
    private var lyricBinding: MusicPlayFragmentLrcBinding? = null

    private var pagerIndex = PAGE_ALBUM
    private var userSeeking = false
    private var hasVisualizerPermission = false
    private var lyricLoadJob: Job? = null
    private var currentLyricSource: String? = null
    private var pendingSeekPositionMs: Int? = null

    private var latestVisualizerState = VisualizerUiState()
    private var lyricPreferences = LyricsSettingPreference()
    private var forwardBackwardSeconds = 15
    private var showForwardBackward = false

    private val currentTrack: Music?
        get() = playerViewModel.playbackState.value.currentTrack

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
        observeTrackMetadata()
        observePlaybackProgress()
        observeVisualizerState()
        observePlayMode()
        observeLyricPreferenceChanges()
        observePlayerPreferences()
    }

    private fun setupUi(binding: FragmentPlayContentBinding) {
        binding.root.applySystemBarInsets(binding.statusBarSpace)
        setupToolbar(binding)
        setupPager(binding)
        setupControls(binding)
        applyLyricPreferences()
        updateForwardBackwardVisibility()
    }

    private fun setupToolbar(binding: FragmentPlayContentBinding) {
        binding.toolbar.navigateBack(this)
        binding.toolbar.inflateMenu(R.menu.menu_activity_music_play)
        binding.toolbar.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.menu_list_menu -> {
                    PlayQueueActivity.startQueue(requireContext(), MusicPlayActivity::class.java.name)
                    true
                }

                else -> false
            }
        }
    }

    private fun setupControls(binding: FragmentPlayContentBinding) = with(binding) {
        musicPlayProgress.musicPlayProgress.apply {
            setThumbColor(ContextCompat.getColor(requireContext(), android.R.color.white))
            setOnSeekBarChangeListener(this@MusicPlayerFragment)
        }

        musicPlayProgress.musicPlayTempo.setOnClickListener(this@MusicPlayerFragment)

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

        val visualizerPage = FragmentMusicPlayVisualizerBinding.inflate(
            inflater,
            binding.musicPlayPager,
            false
        )
        val infoPage = MusicPlayFragmentInfoBinding.inflate(
            inflater,
            binding.musicPlayPager,
            false
        )
        val lyricPage = MusicPlayFragmentLrcBinding.inflate(
            inflater,
            binding.musicPlayPager,
            false
        )

        visualizerBinding = visualizerPage
        infoBinding = infoPage
        lyricBinding = lyricPage

        setupAlbumPage(infoPage)
        setupLyricPage(lyricPage)

        binding.musicPlayPager.adapter = SimpleViewPagerAdapter(
            pages = listOf(
                visualizerPage.root,
                infoPage.root,
                lyricPage.root
            )
        )

        binding.musicPlayPagerIndicator.setViewPager(binding.musicPlayPager)
        binding.musicPlayPagerIndicator.setOnPageChangeListener(this)
        binding.musicPlayPager.currentItem = pagerIndex
        binding.musicPlayPager.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    (activity as? MusicPlayActivity)?.setDismissInterceptionBlocked(true)
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    (activity as? MusicPlayActivity)?.setDismissInterceptionBlocked(false)
                }
            }
            false
        }
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
            val exactSpec = View.MeasureSpec.makeMeasureSpec(targetSize, View.MeasureSpec.EXACTLY)
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
            reloadLyrics(track)
        }

        childFragmentManager.setFragmentResultListener(
            LyricSearchDialogFragment.RESULT_KEY,
            viewLifecycleOwner
        ) { _, _ ->
            currentTrack?.let(::reloadLyrics)
        }
    }

    /**
     * PlayerViewModel.trackUiState should already use distinctUntilChanged by track/favorite/artwork.
     * That removes the need for favoriteOverride and lastRenderedTrackId/lastRenderedArtworkTrackId here.
     */
    private fun observeTrackMetadata() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerViewModel.trackUiState.collect(::renderTrackMetadata)
            }
        }
    }

    private fun observePlaybackProgress() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerViewModel.progressUiState.collect(::renderPlaybackProgress)
            }
        }
    }

    private fun observeVisualizerState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerViewModel.visualizerUiState.collect { state ->
                    latestVisualizerState = state
                    updateLyricAutoScroll()
                    updateVisualizerState()
                }
            }
        }
    }

    private fun renderTrackMetadata(state: TrackUiState) {
        if ((activity as? MusicPlayActivity)?.isDragDismissInProgress() == true) return

        val binding = binding ?: return
        val track = currentTrack
        val lyricPage = lyricBinding

        binding.musicPlayContentTitle.musicPlayName.text =
            state.title.ifBlank { getString(R.string.music) }
        binding.musicPlayContentTitle.musicPlayArtist.text =
            state.artist.ifBlank { getString(R.string.artist) }
        binding.musicPlayFavourite.isSelected = state.isFavorite
        val albumView = infoBinding?.musicPlayAlbum
        if (albumView != null) {
            Glide.with(this)
                .load(state.artworkSource)
                .placeholder(R.drawable.default_album_identify)
                .error(R.drawable.default_album_identify)
                .into(albumView)
        }

        if (track == null) {
            lyricPage?.let { renderEmptyTrackMetadata(binding, it) }
        } else {
            maybeLoadLyrics(track)
        }
    }

    private fun renderEmptyTrackMetadata(
        binding: FragmentPlayContentBinding,
        lyricPage: MusicPlayFragmentLrcBinding
    ) {
        currentLyricSource = null
        lyricLoadJob?.cancel()
        lyricLoadJob = null

        binding.musicPlayContentTitle.musicPlayName.text = getString(R.string.music)
        binding.musicPlayContentTitle.musicPlayArtist.text = getString(R.string.artist)
        binding.musicPlayFavourite.isSelected = false

        infoBinding?.musicPlayAlbum?.setImageResource(R.drawable.default_album_identify_large)

        lyricPage.musicPlayLrc.setLyricText(null)
        lyricPage.root.displayedChild = LYRIC_PAGE_EMPTY
    }

    private fun renderPlaybackProgress(state: PlaybackProgressUiState) {
        if ((activity as? MusicPlayActivity)?.isDragDismissInProgress() == true) return

        val binding = binding ?: return

        val duration = state.durationMs.coerceAtLeast(1L)
        val position = state.positionMs.coerceIn(0L, duration)

        binding.musicPlayController.controlPlayPause.isSelected = state.isPlaying
        binding.musicPlayProgress.musicPlayTotalTime.text = duration.toDurationString()
        binding.musicPlayProgress.musicPlayCurrTime.text = position.toDurationString()
        binding.musicPlayProgress.musicPlayProgress.setMax(duration.toInt())

        if (!userSeeking) {
            binding.musicPlayProgress.musicPlayProgress.setProgress(position.toInt())
        }

        lyricBinding?.musicPlayLrc?.setCurrentTime(position)
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

    private fun observeLyricPreferenceChanges() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                lyricSettingPreferenceStore.lyricPreferences.collect { preferences ->
                    lyricPreferences = preferences
                    applyLyricPreferences()
                    updateLyricAutoScroll()
                }
            }
        }
    }

    private fun observePlayerPreferences() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingPreferencesDataStore.observeSettingPreferences().collect { preferences ->
                    forwardBackwardSeconds = preferences.normal.forwardBackwardSeconds
                    showForwardBackward = preferences.normal.showForwardBackward
                    updateForwardBackwardVisibility()
                }
            }
        }
    }

    private fun updateVisualizerState() {
        if (visualizerBinding == null) return

        val shouldUseVisualizer =
            isResumed &&
                    pagerIndex == PAGE_VISUALIZER &&
                    latestVisualizerState.isPlaying &&
                    latestVisualizerState.audioSessionId != INVALID_AUDIO_SESSION_ID

        if (shouldUseVisualizer && !hasVisualizerPermission) {
            requestVisualizerPermission()
        }

        AudioVisualizerManager.setAudioSessionId(latestVisualizerState.audioSessionId)
        AudioVisualizerManager.setPlaybackActive(latestVisualizerState.isPlaying)
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

        lyricView.setCurrentTextColor(lyricPreferences.lyricColor)
        lyricView.setTextSize(lyricPreferences.lyricTextSize.toInt())
        lyricView.setTextAlign(lyricPreferences.lyricAlign)
        lyricView.setTextTypeface(lyricPreferences.lyricStyle)
    }

    private fun updateLyricAutoScroll() {
        val lyricView = lyricBinding?.musicPlayLrc ?: return
        if (!isAdded) return

        lyricView.setAutoScroll(
            pagerIndex == PAGE_LYRIC &&
                    isResumed &&
                    latestVisualizerState.isPlaying &&
                    lyricView.hasTimedLyrics() &&
                    lyricPreferences.lyricAutoScrollEnabled
        )
    }

    private fun reloadLyrics(track: Music) {
        currentLyricSource = null
        maybeLoadLyrics(track)
    }

    private fun maybeLoadLyrics(track: Music) {
        val lyricPageBinding = lyricBinding ?: return
        val lyricView = lyricPageBinding.musicPlayLrc
        val source = track.data

        if (source == currentLyricSource) {
            lyricPageBinding.root.displayedChild = when {
                lyricView.a() -> LYRIC_PAGE_CONTENT
                source.isNullOrBlank() -> LYRIC_PAGE_EMPTY
                else -> lyricPageBinding.root.displayedChild
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

            if (result.hasLyrics) {
                lyricView.setLyricText(result.text)
                lyricPageBinding.root.displayedChild = LYRIC_PAGE_CONTENT
            } else {
                lyricPageBinding.root.displayedChild = LYRIC_PAGE_EMPTY
            }

            applyLyricPreferences()
            updateLyricAutoScroll()
        }
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.control_play_pause -> onPlayPauseClicked()
            R.id.control_previous -> playerViewModel.playPrevious(requireContext())
            R.id.control_next -> playerViewModel.playNext(requireContext())
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
            R.id.music_play_favourite -> playerViewModel.toggleFavorite(requireContext())
            R.id.music_play_artist -> openArtist()
        }
    }

    private fun onPlayPauseClicked() {
        playerViewModel.onPrimaryPlayPauseClicked(requireContext())
    }

    private fun openTrackOptions() {
        val track = currentTrack ?: return
        CurrentTrackOptionsDialog.newInstance(track)
            .show(parentFragmentManager, CurrentTrackOptionsDialog::class.java.simpleName)
    }

    private fun seekBy(deltaMs: Int) {
        val progress = playerViewModel.progressUiState.value
        val target = (progress.positionMs + deltaMs.toLong())
            .coerceIn(0L, progress.durationMs)
            .toInt()
        playerViewModel.seekTo(requireContext(), target)
    }

    private fun openLyricSearch() {
        val currentTrackId = playerViewModel.trackUiState.value.musicId

        if (currentTrackId == null) {
            ToastUtil.show(requireContext(), getString(R.string.list_is_empty))
            return
        }

        LyricSearchDialogFragment.newInstance(
            trackId = currentTrackId,
            title = playerViewModel.trackUiState.value.title,
            artist = playerViewModel.trackUiState.value.artist,
            audioPath = currentTrack?.data
        ).show(childFragmentManager, LyricSearchDialogFragment.TAG)
    }

    private fun openLyricSettings() {
        val currentTrackId = playerViewModel.trackUiState.value.musicId
        if (currentTrackId == null) {
            ToastUtil.show(requireContext(), getString(R.string.list_is_empty))
            return
        }

        val lyricView = lyricBinding?.musicPlayLrc ?: return
        LyricSettingsDialogFragment.newInstance(
            currentTrackId,
            lyricView.hasTimedLyrics()
        ).show(childFragmentManager, LyricSettingsDialogFragment.TAG)
    }

    private fun seekIncrementMs(): Int {
        return forwardBackwardSeconds.coerceIn(5, 60) * 1_000
    }

    private fun updateForwardBackwardVisibility() {
        val binding = binding ?: return
        val enabled = showForwardBackward
        val seconds = forwardBackwardSeconds

        binding.musicPlayController.controlBackward.isVisible = enabled
        binding.musicPlayController.controlForward.isVisible = enabled

        if (!enabled) return

        binding.musicPlayController.controlBackward.setImageResource(seconds.toBackwardIconRes())
        binding.musicPlayController.controlForward.setImageResource(seconds.toForwardIconRes())
    }

    private fun openArtist() {
        val artistName = playerViewModel.trackUiState.value.artist

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

        val binding = requireBinding()
        binding.musicPlayProgress.musicPlayCurrTime.text = progress.toLong().toDurationString()
        pendingSeekPositionMs = progress
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        userSeeking = true
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        userSeeking = false
        val target = pendingSeekPositionMs ?: return
        pendingSeekPositionMs = null
        playerViewModel.seekTo(requireContext(), target)
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

    fun refreshFromCurrentState() {
        renderTrackMetadata(playerViewModel.trackUiState.value)
        renderPlaybackProgress(playerViewModel.progressUiState.value)
    }

    override fun onPageScrolled(
        position: Int,
        positionOffset: Float,
        positionOffsetPixels: Int
    ) = Unit

    override fun onPageScrollStateChanged(state: Int) {
        (activity as? MusicPlayActivity)?.setDismissInterceptionBlocked(
            state != ViewPager.SCROLL_STATE_IDLE
        )
    }

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
        currentLyricSource = null
        pendingSeekPositionMs = null
        latestVisualizerState = VisualizerUiState()

        visualizerBinding = null
        infoBinding = null
        lyricBinding = null

        AudioVisualizerManager.setUserEnabled(false)
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

    private class SimpleViewPagerAdapter(
        private val pages: List<View>
    ) : PagerAdapter() {

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

    private companion object {
        private const val KEY_PAGER_INDEX = "pager_index"

        private const val PAGE_VISUALIZER = 0
        private const val PAGE_ALBUM = 1
        private const val PAGE_LYRIC = 2

        private const val LYRIC_PAGE_LOADING = 0
        private const val LYRIC_PAGE_CONTENT = 1
        private const val LYRIC_PAGE_EMPTY = 3

        private const val INVALID_AUDIO_SESSION_ID = -1
    }
}
