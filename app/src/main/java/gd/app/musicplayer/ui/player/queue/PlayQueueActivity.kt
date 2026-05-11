package gd.app.musicplayer.ui.player.queue

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.databinding.ActivityPlayQueueBinding
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.player.full.MusicPlayActivity
import gd.app.musicplayer.ui.player.full.PlayerViewModel
import kotlinx.coroutines.Job

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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        resolveCaller(intent)
        setupBackground()
        applyCurrentArtwork()
        observeArtworkIfNeeded()
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
        binding.mainBackground.setBackgroundResource(R.drawable.th_music_large)
    }

    private fun applyCurrentArtwork() {
//        val track = playbackViewModel.playbackState.value.currentTrack ?: return
//        binding.musicPlaySkin.loadBlurredArtworkBackground(track.albumPicture)
    }

    private fun observeArtworkIfNeeded() {
//        artworkJob?.cancel()
//        if (!isFromMusicPlayActivity) return
//        artworkJob = lifecycleScope.launch {
//            playbackViewModel.playbackState.collect { state ->
//                binding.musicPlaySkin.loadBlurredArtworkBackground(state.currentTrack?.albumPicture)
//            }
//        }
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
                QueueControlFragment(),
                QueueControlFragment::class.java.simpleName
            )
            .commitNow()
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