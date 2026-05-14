package gd.app.musicplayer.ui.player.queue

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.core.common.extension.loadBlurredArtworkBackground
import gd.app.musicplayer.databinding.ActivityPlayQueueBinding
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.player.full.MusicPlayActivity
import gd.app.musicplayer.ui.player.full.PlayerViewModel
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
        setupBackground()
        applyCurrentArtwork()
        observeArtworkIfNeeded()

        if (savedInstanceState == null) {
            showQueueScreen()
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

    private fun setupBackground() {
        if (isFromMusicPlayActivity) {
            binding.mainBackground.setBackgroundResource(R.drawable.th_music_large)
            binding.musicPlaySkin.visibility = View.VISIBLE
        } else {
            applyThemeTo(binding.mainBackground)
            binding.musicPlaySkin.visibility = View.GONE
            binding.musicPlaySkin.setImageDrawable(null)
        }
    }

    private fun applyCurrentArtwork() {
        if (!isFromMusicPlayActivity) return
        binding.musicPlaySkin.loadBlurredArtworkBackground(
            playbackViewModel.playbackState.value.currentTrack?.albumPicture
        )
    }

    private fun observeArtworkIfNeeded() {
        artworkJob?.cancel()
        artworkJob = null
        if (!isFromMusicPlayActivity) return
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
