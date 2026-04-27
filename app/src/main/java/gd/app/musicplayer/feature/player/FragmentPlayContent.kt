package gd.app.musicplayer.feature.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import dagger.hilt.android.AndroidEntryPoint
import gd.app.lib.model.lrc.view.LyricView
import gd.app.lib.model.visualizer.VisualizerView
import gd.app.lib.view.square.SquareCornerFrameLayout
import gd.app.lib.view.square.c
import gd.app.musicplayer.R
import gd.app.musicplayer.core.ui.extension.applySystemBarInsets
import gd.app.musicplayer.core.ui.extension.navigateBack
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.model.isFavorite
import gd.app.musicplayer.data.model.loadMusicArtwork
import gd.app.musicplayer.databinding.FragmentPlayContentBinding
import gd.app.musicplayer.feature.equalizer.EffectGroupActivity
import gd.app.musicplayer.feature.equalizer.EqualizerActivity
import gd.app.musicplayer.feature.library.AlbumMusicActivity
import gd.app.musicplayer.feature.library.CurrentTrackOptionsDialog
import gd.app.musicplayer.feature.lyrics.FullLyricDialogFragment
import gd.app.musicplayer.feature.lyrics.LyricAdjustDialogFragment
import gd.app.musicplayer.feature.lyrics.LyricSearchDialogFragment
import gd.app.musicplayer.feature.lyrics.LyricSettingsDialogFragment
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.ui.common.view.SeekBar
import gd.app.musicplayer.util.LyricsLoader
import gd.app.musicplayer.util.PreferenceUtil
import gd.app.musicplayer.util.TrackLyricsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FragmentPlayContent : ViewBindingFragment<FragmentPlayContentBinding>(), View.OnClickListener,
    SeekBar.OnSeekBarChangeListener,
    ViewPager.OnPageChangeListener {

    private val viewModel: MusicPlayViewModel by viewModels()

    private lateinit var lyricFlipper: ViewFlipper
    private lateinit var lyricView: LyricView
    private lateinit var albumImage: ImageView
    private lateinit var visualizerView: VisualizerView

    private lateinit var musicPlayName: TextView
    private lateinit var musicPlayArtist: TextView
    private lateinit var musicPlayCurrTime: TextView
    private lateinit var musicPlayTotalTime: TextView
    private lateinit var musicPlayProgress: SeekBar
    private lateinit var controlPlayPause: ImageView
    private lateinit var controlMode: ImageView
    private lateinit var controlBackward: ImageView
    private lateinit var controlPrevious: ImageView
    private lateinit var controlNext: ImageView
    private lateinit var controlForward: ImageView
    private lateinit var controlEqualizer: ImageView
    private lateinit var musicPlayTempo: ImageView

    private var pagerIndex = 1
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


    override fun onCreateBinding(inflater: LayoutInflater): FragmentPlayContentBinding =
        FragmentPlayContentBinding.inflate(inflater)

    override fun onBindingCreated(
        binding: FragmentPlayContentBinding,
        savedInstanceState: Bundle?
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        pagerIndex = savedInstanceState?.getInt("pager_index", 1) ?: 1
        hasVisualizerPermission = hasVisualizerPermission()
        setupUi()
        setupFragmentResults()
        observePlayback()
        observeLyricPreferenceChanges()
    }

    private fun setupUi() {
        val binding = requireBinding()
        binding.root.applySystemBarInsets(binding.statusBarSpace)
        setupToolbar()
        setupPager()

        musicPlayName = binding.root.findViewById(R.id.music_play_name)
        musicPlayArtist = binding.root.findViewById(R.id.music_play_artist)
        musicPlayCurrTime = binding.root.findViewById(R.id.music_play_curr_time)
        musicPlayTotalTime = binding.root.findViewById(R.id.music_play_total_time)
        musicPlayProgress = binding.root.findViewById(R.id.music_play_progress)
        controlPlayPause = binding.root.findViewById(R.id.control_play_pause)
        controlMode = binding.root.findViewById(R.id.control_mode)
        controlBackward = binding.root.findViewById(R.id.control_backward)
        controlPrevious = binding.root.findViewById(R.id.control_previous)
        controlNext = binding.root.findViewById(R.id.control_next)
        controlForward = binding.root.findViewById(R.id.control_forward)
        controlEqualizer = binding.root.findViewById(R.id.control_equalizer)
        musicPlayTempo = binding.root.findViewById(R.id.music_play_tempo)

        musicPlayProgress.setThumbColor(
            ContextCompat.getColor(
                requireContext(),
                android.R.color.white
            )
        )
        musicPlayProgress.setOnSeekBarChangeListener(this)

        controlPlayPause.setOnClickListener(this)
        controlMode.setOnClickListener(this)
        controlBackward.setOnClickListener(this)
        controlPrevious.setOnClickListener(this)
        controlNext.setOnClickListener(this)
        controlForward.setOnClickListener(this)
        controlEqualizer.setOnClickListener(this)
        binding.musicPlayFavourite.setOnClickListener(this)
        musicPlayTempo.setOnClickListener(this)
        binding.musicPlaySoundEffect.setOnClickListener(this)
        binding.musicPlayLyricSearch.setOnClickListener(this)
        binding.musicLyricSetting.setOnClickListener(this)
        binding.musicPlayMore.setOnClickListener(this)
        musicPlayArtist.setOnClickListener(this)

        applyLyricPreferences()
        refreshPlayModeIcon()
        updateForwardBackwardVisibility()
    }

    private fun setupToolbar() {
        val binding = requireBinding()
        binding.toolbar.navigateBack(this)
        binding.toolbar.inflateMenu(R.menu.menu_activity_music_play)
        binding.toolbar.setOnMenuItemClickListener(Toolbar.OnMenuItemClickListener { item: MenuItem ->
            if (item.itemId == R.id.menu_list_menu) {
                ActivityPlayQueue.startQueue(requireContext())
            }
            true
        })
    }

    private fun setupPager() {
        val binding = requireBinding()
        val inflater = LayoutInflater.from(requireContext())
        val visualizer =
            inflater.inflate(R.layout.fragment_music_play_visualizer, binding.musicPlayPager, false)
        val info =
            inflater.inflate(R.layout.music_play_fragment_info, binding.musicPlayPager, false)
        lyricFlipper = inflater.inflate(
            R.layout.music_play_fragment_lrc,
            binding.musicPlayPager,
            false
        ) as ViewFlipper

        visualizerView = visualizer as VisualizerView
        lyricView = lyricFlipper.findViewById(R.id.music_play_lrc)
        albumImage = info.findViewById(R.id.music_play_album)
        info.findViewById<SquareCornerFrameLayout>(R.id.layout_music_play_album_parent)
            .setSquare(object : c.a {
                override fun a(widthMeasureSpec: Int, heightMeasureSpec: Int): IntArray {
                    val size = minOf(
                        View.MeasureSpec.getSize(widthMeasureSpec),
                        View.MeasureSpec.getSize(heightMeasureSpec)
                    )
                    val spec = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
                    return intArrayOf(spec, spec)
                }
            })

        lyricFlipper.findViewById<View>(R.id.music_play_lrc_search).setOnClickListener(this)
        lyricView.setOnClickListener {
            if (lyricView.a()) {
                FullLyricDialogFragment.Companion.show(childFragmentManager)
            }
        }

        val pages = arrayListOf(visualizer, info, lyricFlipper)
        binding.musicPlayPager.adapter = object : PagerAdapter() {
            override fun getCount(): Int = pages.size
            override fun isViewFromObject(view: View, `object`: Any): Boolean = view === `object`
            override fun instantiateItem(container: ViewGroup, position: Int): Any {
                val page = pages[position]
                container.addView(page)
                return page
            }

            override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
                container.removeView(`object` as View)
            }
        }
        binding.musicPlayPagerIndicator.setViewPager(binding.musicPlayPager)
        binding.musicPlayPagerIndicator.setOnPageChangeListener(this)
        binding.musicPlayPager.currentItem = pagerIndex
        onPageSelected(pagerIndex)
    }

    private fun setupFragmentResults() {
        childFragmentManager.setFragmentResultListener(
            LyricSettingsDialogFragment.Companion.RESULT_KEY,
            viewLifecycleOwner
        ) { _, _ ->
            applyLyricPreferences()
            updateLyricAutoScroll()
        }
        childFragmentManager.setFragmentResultListener(
            LyricAdjustDialogFragment.Companion.RESULT_KEY,
            viewLifecycleOwner
        ) { _, _ ->
            currentTrack?.let { track ->
                lyricView.setTimeOffset(
                    TrackLyricsStore.from(requireContext()).getTrackLyricOffset(track._id)
                )
                maybeLoadLyrics(track)
            }
        }
        childFragmentManager.setFragmentResultListener(
            LyricSearchDialogFragment.Companion.RESULT_KEY,
            viewLifecycleOwner
        ) { _, _ ->
            currentLyricSource = null
            currentTrack?.let(::maybeLoadLyrics)
        }
    }

    private fun observePlayback() {
        val binding = requireBinding()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.playbackState.collect { state ->
                    currentTrack = state.currentTrack
                    val track = state.currentTrack
                    if (track == null) {
                        lyricLoadJob?.cancel()
                        currentLyricSource = null
                        currentAudioSessionId = -1
                        isPlaybackActive = false
                        musicPlayName.text = getString(android.R.string.unknownName)
                        musicPlayArtist.text = getString(android.R.string.unknownName)
                        musicPlayCurrTime.text = viewModel.formatTime(0)
                        musicPlayTotalTime.text = viewModel.formatTime(0)
                        musicPlayProgress.setMax(1)
                        musicPlayProgress.setProgress(0)
                        controlPlayPause.isSelected = false
                        binding.musicPlayFavourite.isSelected = false
                        lyricView.setLyricText(null)
                        lyricFlipper.displayedChild = 0
                        updateVisualizerState()
                        return@collect
                    }

                    currentAudioSessionId = state.audioSessionId
                    isPlaybackActive = state.isPlaying
                    musicPlayName.text = track.title
                    musicPlayArtist.text = track.artist
                    controlPlayPause.isSelected = state.isPlaying
                    musicPlayTotalTime.text = viewModel.formatTime(state.durationMs)
                    musicPlayCurrTime.text = viewModel.formatTime(state.positionMs)
                    musicPlayProgress.setMax(state.durationMs.coerceAtLeast(1))
                    if (!userSeeking) {
                        musicPlayProgress.setProgress(state.positionMs)
                    }
                    lyricView.setCurrentTime(state.positionMs.toLong())
                    val fav = favoriteOverride ?: track.isFavorite()
                    binding.musicPlayFavourite.isSelected = fav
                    track.loadMusicArtwork(albumImage)
                    maybeLoadLyrics(track)
                    updateLyricAutoScroll()
                    updateVisualizerState()
                }
            }
        }
    }

    private fun observeLyricPreferenceChanges() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                PreferenceUtil.getInstance(requireContext()).observePreferenceChanges(
                    "preference_lyric_color",
                    "preference_lyric_text_size",
                    "lyric_auto_scroll",
                    "lyric_align",
                    "lyric_style"
                ).collect {
                    applyLyricPreferences()
                    updateLyricAutoScroll()
                }
            }
        }
    }

    private fun updateVisualizerState() {
        if (!::visualizerView.isInitialized) return

        val shouldUseVisualizer =
            isResumed &&
                    pagerIndex == 0 &&
                    isPlaybackActive &&
                    currentAudioSessionId > 0

        if (shouldUseVisualizer && !hasVisualizerPermission) {
            requestVisualizerPermission()
        }

        visualizerView.setAudioSessionId(currentAudioSessionId)
        visualizerView.setActive(shouldUseVisualizer && hasVisualizerPermission)
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
        if (!::lyricView.isInitialized || !isAdded) return
        val prefs = PreferenceUtil.getInstance(requireContext())
        lyricView.setCurrentTextColor(prefs.getLyricColor())
        lyricView.setTextSize(prefs.getLyricTextSize().toFloat())
        lyricView.setTextAlign(prefs.getLyricAlign())
        lyricView.setTextTypeface(prefs.getLyricStyle())
    }

    private fun updateLyricAutoScroll() {
        if (!::lyricView.isInitialized || !isAdded) return
        val prefs = PreferenceUtil.getInstance(requireContext())
        lyricView.setAutoScroll(
            pagerIndex == 2 &&
                isResumed &&
                isPlaybackActive &&
                lyricView.hasTimedLyrics() &&
                prefs.isLyricAutoScrollEnabled()
        )
    }

    private fun maybeLoadLyrics(track: Music) {
        val source = track.data
        if (source == currentLyricSource) {
            if (lyricView.a()) {
                lyricFlipper.displayedChild = 1
            } else if (source.isNullOrBlank()) {
                lyricFlipper.displayedChild = 3
            }
            return
        }

        currentLyricSource = source
        lyricLoadJob?.cancel()
        lyricView.setTimeOffset(TrackLyricsStore.from(requireContext()).getTrackLyricOffset(track._id))

        if (source.isNullOrBlank()) {
            lyricView.setLyricText(null)
            lyricFlipper.displayedChild = 3
            return
        }

        lyricView.setLyricText(null)
        lyricFlipper.displayedChild = 0
        lyricLoadJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = LyricsLoader.load(requireContext(), track._id, source)
            if (!isAdded || currentLyricSource != source) return@launch
            lyricView.setTimeOffset(
                TrackLyricsStore.from(requireContext()).getTrackLyricOffset(track._id)
            )
            if (!result.hasLyrics) {
                lyricFlipper.displayedChild = 3
            } else {
                lyricView.setLyricText(result.text)
                lyricFlipper.displayedChild = 1
            }
            applyLyricPreferences()
            updateLyricAutoScroll()
        }
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.control_play_pause -> viewModel.togglePlayPause(requireContext())
            R.id.control_previous -> viewModel.playPrevious(requireContext())
            R.id.control_next -> viewModel.playNext(requireContext())
            R.id.control_backward -> seekBy(-seekIncrementMs())
            R.id.control_forward -> seekBy(seekIncrementMs())
            R.id.control_equalizer -> EqualizerActivity.Companion.start(requireContext())
            R.id.music_play_sound_effect -> EffectGroupActivity.Companion.start(requireContext())
            R.id.control_mode -> {
                cyclePlayMode()
                refreshPlayModeIcon()
            }

            R.id.music_play_more -> {
                val track = currentTrack ?: return
                CurrentTrackOptionsDialog.Companion.newInstance(track)
                    .show(parentFragmentManager, CurrentTrackOptionsDialog::class.java.simpleName)
            }
            R.id.music_play_lrc_search, R.id.music_play_lyric_search -> openLyricSearch()
            R.id.music_lyric_setting -> openLyricSettings()

            R.id.music_play_tempo -> TempoDialogFragment.show(childFragmentManager)
            R.id.music_play_favourite -> toggleFavorite()
            R.id.music_play_artist -> openArtist()
        }
    }

    private fun seekBy(deltaMs: Int) {
        val state = viewModel.playbackState.value
        val target = (state.positionMs + deltaMs).coerceIn(0, state.durationMs)
        viewModel.seekTo(requireContext(), target)
    }

    private fun openLyricSearch() {
        val track = currentTrack ?: run {
            ToastUtil.show(requireContext(), getString(R.string.list_is_empty))
            return
        }
        LyricSearchDialogFragment.Companion.newInstance(track._id, track.title, track.artist, track.data)
            .show(childFragmentManager, LyricSearchDialogFragment.Companion.TAG)
    }

    private fun openLyricSettings() {
        val track = currentTrack ?: run {
            ToastUtil.show(requireContext(), getString(R.string.list_is_empty))
            return
        }
        LyricSettingsDialogFragment.Companion.newInstance(track._id, lyricView.hasTimedLyrics())
            .show(childFragmentManager, LyricSettingsDialogFragment.Companion.TAG)
    }

    private fun seekIncrementMs(): Int {
        val seconds = PreferenceUtil.getInstance(requireContext())
            .getIntPreference("time_forward_backward", 15)
        return seconds.coerceIn(5, 60) * 1_000
    }

    private fun updateForwardBackwardVisibility() {
        val enabled = PreferenceUtil.getInstance(requireContext())
            .getBooleanPreference("show_forward_backward", false)
        val seconds = PreferenceUtil.getInstance(requireContext())
            .getIntPreference("time_forward_backward", 15)
        controlBackward.isVisible = enabled
        controlForward.isVisible = enabled

        if (!enabled) return
        controlBackward.setImageResource(
            when (seconds) {
                5 -> {
                    R.drawable.vector_backward_5
                }

                10 -> {
                    R.drawable.vector_backward_10
                }

                15 -> {
                    R.drawable.vector_backward_15
                }

                20 -> {
                    R.drawable.vector_backward_20
                }

                30 -> {
                    R.drawable.vector_backward_30
                }

                else -> {
                    R.drawable.vector_backward_60
                }
            }
        )

        controlForward.setImageResource(
            when (seconds) {
                5 -> {
                    R.drawable.vector_forward_5
                }

                10 -> {
                    R.drawable.vector_forward_10
                }

                15 -> {
                    R.drawable.vector_forward_15
                }

                20 -> {
                    R.drawable.vector_forward_20
                }

                30 -> {
                    R.drawable.vector_forward_30
                }

                else -> {
                    R.drawable.vector_forward_60
                }
            }
        )
    }

    private fun cyclePlayMode() {
        val pref = PreferenceUtil.getInstance(requireContext())
        val nextMode = when (pref.getPlayMode()) {
            1 -> 2
            2 -> 3
            else -> 1
        }
        pref.setPlayMode(nextMode)
    }

    private fun refreshPlayModeIcon() {
        controlMode.setImageResource(R.drawable.vector_mode_order)
        controlMode.isSelected = PreferenceUtil.getInstance(requireContext()).getPlayMode() != 1
    }

    private fun toggleFavorite() {
        val binding = requireBinding()
        val track = currentTrack ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            favoriteOverride = viewModel.toggleFavorite(track._id)
            launch(Dispatchers.Main) {
                binding.musicPlayFavourite.isSelected = favoriteOverride == true
            }
        }
    }

    private fun openArtist() {
        val track = currentTrack ?: return
        val artistName = track.artist.takeIf { it.isNotBlank() } ?: return
        AlbumMusicActivity.Companion.start(
            requireContext(),
            MusicSet.Artist(
                id = MusicSet.ARTISTS_ID,
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
        if (fromUser) {
            musicPlayCurrTime.text = viewModel.formatTime(progress)
            viewModel.seekTo(requireContext(), progress)
        }
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        userSeeking = false
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        userSeeking = true
    }

    override fun onPageSelected(position: Int) {
        val binding = requireBinding()
        pagerIndex = position
        val lyricPage = position == 2
        binding.musicPlaySoundEffect.isVisible = !lyricPage
        binding.musicPlayLyricSearch.isVisible = lyricPage
        binding.musicLyricSetting.isVisible = lyricPage
        updateLyricAutoScroll()
        updateVisualizerState()
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) =
        Unit

    override fun onPageScrollStateChanged(state: Int) = Unit

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("pager_index", pagerIndex)
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
        if (::lyricView.isInitialized) {
            lyricView.setAutoScroll(false)
        }
        if (::visualizerView.isInitialized) {
            visualizerView.setActive(false)
        }
        super.onPause()
    }
}
