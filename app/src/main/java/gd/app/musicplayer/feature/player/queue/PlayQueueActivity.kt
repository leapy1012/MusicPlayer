package gd.app.musicplayer.feature.player.queue

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.core.common.extension.loadBlurredArtworkBackground
import gd.app.musicplayer.core.designsystem.theme.ThemeManager
import gd.app.musicplayer.core.designsystem.theme.ThemePalette
import gd.app.musicplayer.databinding.ActivityPlayQueueBinding
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.feature.player.full.MusicPlayActivity
import gd.app.musicplayer.feature.player.full.PlayerViewModel
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PlayQueueActivity : BaseActivity() {

    private val playbackViewModel: PlayerViewModel by viewModels()
    private lateinit var binding: ActivityPlayQueueBinding
    private var isFromMusicPlayActivity = false
    private var artworkJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPlayQueueBinding.inflate(layoutInflater)
        setContentView(binding.root)

        resolveCaller(intent)
        // Caller is known only after extras — refresh bars for dark artwork plate vs light list.
        refreshSystemBarAppearance()
        setupBackground()
        applyCurrentArtwork()
        observeArtworkIfNeeded()

        if (savedInstanceState == null) {
            showQueueScreen()
        }
    }

    /**
     * Artwork plate from the player is always dark → light system icons.
     * Otherwise follow the header surface (light theme → dark icons).
     */
    override fun prefersLightSystemBars(palette: ThemePalette): Boolean {
        return if (usesArtworkBackground()) {
            false
        } else {
            palette.isHeaderSurfaceLight()
        }
    }

    override fun onResume() {
        super.onResume()
        setupBackground()
        applyCurrentArtwork()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        resolveCaller(intent)
        refreshSystemBarAppearance()
        setupBackground()
        applyCurrentArtwork()
        observeArtworkIfNeeded()
        refreshQueueBanner()
    }

    override fun onDestroy() {
        artworkJob?.cancel()
        artworkJob = null
        super.onDestroy()
    }

    private fun resolveCaller(intent: Intent?) {
        val fromClass = intent?.getStringExtra(EXTRA_FROM_CLASS).orEmpty()
        isFromMusicPlayActivity = fromClass == MusicPlayActivity::class.java.name
    }

    private fun usesArtworkBackground(): Boolean =
        isFromMusicPlayActivity &&
            themeEngine.currentTheme().getThemeType() != ThemeManager.THEME_TYPE_LIGHT

    private fun setupBackground() {
        if (usesArtworkBackground()) {
            binding.mainBackground.setBackgroundResource(R.drawable.th_music_large)
            binding.musicPlaySkin.visibility = View.VISIBLE
            binding.mainContentView.setBackgroundColor(ARTWORK_SCRIM_COLOR)
        } else {
            applyThemeTo(binding.mainBackground)
            binding.musicPlaySkin.visibility = View.GONE
            binding.musicPlaySkin.setImageDrawable(null)
            binding.mainContentView.background = null
        }
    }

    private fun applyCurrentArtwork() {
        if (!usesArtworkBackground()) return
        binding.musicPlaySkin.loadBlurredArtworkBackground(
            playbackViewModel.playbackState.value.currentTrack?.albumPicture
        )
    }

    private fun observeArtworkIfNeeded() {
        artworkJob?.cancel()
        artworkJob = null
        if (!usesArtworkBackground()) return
        artworkJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                playbackViewModel.playbackState
                    .map { state -> state.currentTrack?.albumPicture }
                    .distinctUntilChanged()
                    .collect { artworkPath ->
                        binding.musicPlaySkin.loadBlurredArtworkBackground(artworkPath)
                    }
            }
        }
    }

    private fun showQueueScreen() {
        binding.mainFragmentBanner.visibility = View.VISIBLE

        supportFragmentManager.beginTransaction()
            .replace(
                binding.mainFragmentContainer.id,
                PlaybackQueueFragment(),
                PlaybackQueueFragment::class.java.simpleName
            )
            .replace(
                binding.mainFragmentBanner.id,
                createQueueControlFragment(),
                QueueControlFragment::class.java.simpleName
            )
            .commitNow()
    }

    private fun refreshQueueBanner() {
        if (!supportFragmentManager.isStateSaved) {
            supportFragmentManager.beginTransaction()
                .replace(
                    binding.mainFragmentBanner.id,
                    createQueueControlFragment(),
                    QueueControlFragment::class.java.simpleName
                )
                .commitNow()
        }
    }

    private fun createQueueControlFragment(): QueueControlFragment {
        return QueueControlFragment.newInstance(
            if (isFromMusicPlayActivity) MusicPlayActivity::class.java.name else ""
        )
    }

    companion object {
        private const val EXTRA_FROM_CLASS = "from_class"
        private const val ARTWORK_SCRIM_COLOR = 0x66000000

        fun start(context: Context) {
            startQueue(context, context.javaClass.name)
        }

        fun startQueue(context: Context) {
            startQueue(context, context.javaClass.name)
        }

        fun startQueue(context: Context, fromClass: String) {
            context.startActivityCompat(
                Intent(context, PlayQueueActivity::class.java).apply {
                    putExtra(EXTRA_FROM_CLASS, fromClass)
                }
            )
        }
    }
}
