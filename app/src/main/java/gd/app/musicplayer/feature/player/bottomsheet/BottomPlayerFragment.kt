package gd.app.musicplayer.feature.player.bottomsheet

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applyRoundedOutline
import gd.app.musicplayer.core.common.extension.loadMusicArtworkLarge
import gd.app.musicplayer.core.common.extension.toDurationString
import gd.app.musicplayer.core.designsystem.theme.ThemeManager
import gd.app.musicplayer.core.designsystem.theme.ThemePalette
import gd.app.musicplayer.core.designsystem.view.SeekBar
import gd.app.musicplayer.databinding.FragmentMainControl2Binding
import gd.app.musicplayer.feature.player.common.PlaybackChromeSnapshot
import gd.app.musicplayer.feature.player.common.PlaybackProgressBinder
import gd.app.musicplayer.ui.common.base.BasePlayerSheetActivity
import gd.app.musicplayer.ui.common.base.PlaybackQueueBottomSheetFragment
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.feature.player.full.MusicPlayActivity
import gd.app.musicplayer.feature.player.full.PlaybackProgressUiState
import gd.app.musicplayer.feature.player.full.PlayerViewModel
import gd.app.musicplayer.feature.player.full.TrackUiState
import gd.app.musicplayer.playback.PlaybackController
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BottomPlayerFragment : ViewBindingFragment<FragmentMainControl2Binding>(),
    SeekBar.OnSeekBarChangeListener {

    private val viewModel: PlayerViewModel by activityViewModels()

    @Inject lateinit var playbackController: PlaybackController

    private var userSeeking = false

    override fun onCreateBinding(inflater: LayoutInflater): FragmentMainControl2Binding {
        return FragmentMainControl2Binding.inflate(inflater)
    }

    override fun onBindingCreated(
        binding: FragmentMainControl2Binding,
        savedInstanceState: Bundle?
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        setupInsets(binding)
        setupClickListeners(binding)
        // Sync first paint from process singleton (original d0/e0 → y6.y).
        paintFromPlaybackSingleton()
        observeTrackMetadata()
        observePlaybackProgress()
        paintLightPanelSurface()
    }

    override fun onThemeChanged(palette: ThemePalette?) {
        super.onThemeChanged(palette)
        paintLightPanelSurface()
    }

    /**
     * Light theme: solid white plate + dark chrome (XML defaults are pictured wash + white icons).
     * Picture/dark keep the translucent black overlay so wallpaper still reads through.
     */
    private fun paintLightPanelSurface() {
        val binding = binding ?: return
        val palette = themeEngine.currentTheme()
        if (palette.getThemeType() == ThemeManager.THEME_TYPE_LIGHT) {
            val typed = binding.root.context.obtainStyledAttributes(
                intArrayOf(com.coui.appcompat.R.attr.couiColorCardBackground)
            )
            val color = typed.getColor(0, Color.WHITE)
            typed.recycle()
            binding.root.setBackgroundColor(color)
        } else {
            binding.root.setBackgroundColor(0x33000000)
        }
    }

    private fun paintFromPlaybackSingleton() {
        val state = playbackController.state.value
        renderTrackMetadata(PlaybackChromeSnapshot.trackUiState(state))
        renderPlaybackProgress(PlaybackChromeSnapshot.progressUiState(state))
    }

    private fun observeTrackMetadata() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.trackUiState.collect { state ->
                    renderTrackMetadata(state)
                }
            }
        }
    }

    private fun observePlaybackProgress() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.progressUiState.collect { state -> renderPlaybackProgress(state) }
            }
        }
    }

    private fun renderTrackMetadata(state: TrackUiState) {
        val binding = requireBinding()

        binding.mainControlTitle.text = state.title.ifBlank {
            getString(R.string.music)
        }
        binding.mainControlArtist.text = state.artist.ifBlank {
            getString(R.string.artist)
        }
        binding.mainControlLeft.isSelected = state.isFavorite
        binding.mainControlAlbum.applyRoundedOutline(R.dimen.item_image_corner_radius)
        binding.mainControlAlbum.loadMusicArtworkLarge(
            state.artworkSource ?: R.drawable.default_album_identify
        )
    }

    private fun renderPlaybackProgress(state: PlaybackProgressUiState) {
        val binding = binding ?: return

        PlaybackProgressBinder.bind(
            seekBar = binding.mainControlProgress,
            durationMs = state.durationMs,
            positionMs = state.positionMs,
            userSeeking = userSeeking,
            isPlaying = state.isPlaying,
            playPauseView = binding.mainControlPlayPause,
            currentTimeView = binding.mainControlCurrTime,
            totalTimeView = binding.mainControlTotalTime
        )
    }

    private fun setupInsets(binding: FragmentMainControl2Binding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            // One nav pad only: plate draws behind the 3-button icons (sheet already
            // sized +nav; Material gesture inset padding is ignored on the sheet).
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            if (view.paddingBottom != bottomInset) {
                view.updatePadding(bottom = bottomInset)
            }
            insets
        }

        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun setupClickListeners(binding: FragmentMainControl2Binding) = with(binding) {
        mainControlPlayPause.setOnClickListener { onPlayPauseClicked() }
        mainControlPrevious.setOnClickListener { viewModel.playPrevious(requireContext()) }
        mainControlNext.setOnClickListener { viewModel.playNext(requireContext()) }
        mainControlBack.setOnClickListener { collapsePlayerPanel() }
        mainControlRight.setOnClickListener { showPlaybackQueue() }
        mainControlLeft.setOnClickListener { viewModel.toggleFavorite(requireContext()) }
        mainControlAlbum.setOnClickListener { openPlayerScreen() }
        mainControlTitle.setOnClickListener { openPlayerScreen() }
        mainControlArtist.setOnClickListener { openPlayerScreen() }

        mainControlProgress.setOnSeekBarChangeListener(this@BottomPlayerFragment)
    }

    private fun onPlayPauseClicked() {
        viewModel.onPrimaryPlayPauseClicked(requireContext())
    }

    private fun collapsePlayerPanel() {
        (activity as? BasePlayerSheetActivity)?.collapsePlayerPanel()
    }

    private fun showPlaybackQueue() {
        PlaybackQueueBottomSheetFragment.show(childFragmentManager)
    }

    private fun openPlayerScreen() {
        MusicPlayActivity.start(requireContext())
    }

    override fun onProgressChanged(
        seekBar: SeekBar,
        progress: Int,
        fromUser: Boolean
    ) {
        if (!fromUser) return

        val binding = binding ?: return
        binding.mainControlCurrTime.text = progress.toLong().toDurationString()
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        userSeeking = false
        viewModel.seekTo(requireContext(), seekBar.getProgress())
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        userSeeking = true
    }
}
