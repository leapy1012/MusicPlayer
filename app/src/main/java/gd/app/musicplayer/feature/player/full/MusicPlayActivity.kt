package gd.app.musicplayer.feature.player.full

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import gd.app.lib.view.DragDismissLayout
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.loadBlurredArtworkBackground
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.databinding.ActivityMusicplayBinding
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.model.ViewFlipHelper
import gd.app.musicplayer.feature.lyrics.FullLyricFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MusicPlayActivity : BaseActivity(), DragDismissLayout.OnDismissListener {

    private val playbackViewModel: PlayerViewModel by viewModels()

    private lateinit var binding: ActivityMusicplayBinding
    private lateinit var backgroundImage: View

    private val viewFlipper = ViewFlipHelper()

    private var artworkJob: Job? = null
    private var isRecordAudioGranted = false
    private var lastArtworkPath: String? = null
    private var dragDismissInProgress = false
    private var pendingArtworkPath: String? = null
    private var dismissInterceptionBlocked = false

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            isRecordAudioGranted = granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMusicplayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyThemeTo(binding.root)

        setupViews()
        setupDragDismiss()
        setupViewFlipper(savedInstanceState)
        setupInitialFragment(savedInstanceState)

        isRecordAudioGranted = hasRecordAudioPermission()

        observePlaybackArtwork()
    }

    private fun setupViews() {
        backgroundImage = binding.musicPlaySkin
    }

    private fun setupDragDismiss() {
        binding.dragDismissLayout.apply {
            setAllowedDirections(DragDismissLayout.Direction.DOWN)
            setOnDismissListener(this@MusicPlayActivity)
            setOnDragStateListener(
                object : DragDismissLayout.OnDragStateListener {
                    override fun onDragStarted() {
                        dragDismissInProgress = true
                    }

                    override fun onDragProgress(progress: Float) = Unit

                    override fun onDragFinished(dismissed: Boolean) {
                        dragDismissInProgress = false
                        binding.dragDismissLayout.setDisallowInterceptTouchEvent(
                            dismissInterceptionBlocked
                        )
                        if (!dismissed) {
                            pendingArtworkPath?.let(::applyArtworkIfChanged)
                            pendingArtworkPath = null
                            refreshVisiblePlayerPage()
                        }
                    }
                }
            )
        }
    }

    private fun setupViewFlipper(savedInstanceState: Bundle?) {
        viewFlipper.bindViews(
            binding.root,
            R.id.music_play_content,
            R.id.music_play_lyric_container
        )

        val restoredIndex = savedInstanceState?.getInt(
            KEY_FLIP_POSITION,
            DEFAULT_FLIP_POSITION
        ) ?: DEFAULT_FLIP_POSITION

        viewFlipper.flipTo(restoredIndex)
    }

    private fun setupInitialFragment(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) return

        supportFragmentManager.beginTransaction()
            .replace(
                binding.musicPlayContent.id,
                MusicPlayerFragment(),
                MusicPlayerFragment::class.java.simpleName
            )
            .commit()
    }

    private fun observePlaybackArtwork() {
        artworkJob?.cancel()

        artworkJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                playbackViewModel.playbackState
                    .map { state ->
                        state.currentTrack?.albumPicture
                    }
                    .distinctUntilChanged()
                    .collect { artworkPath ->
                        if (dragDismissInProgress) {
                            pendingArtworkPath = artworkPath
                        } else {
                            pendingArtworkPath = null
                            applyArtworkIfChanged(artworkPath)
                        }
                    }
            }
        }
    }

    private fun applyArtworkIfChanged(artworkPath: String?) {
        if (lastArtworkPath == artworkPath) return

        lastArtworkPath = artworkPath

        binding.musicPlaySkin.loadBlurredArtworkBackground(artworkPath)
    }

    fun showLyrics() {
        if (supportFragmentManager.findFragmentByTag(FullLyricFragment.TAG) == null) {
            supportFragmentManager.beginTransaction()
                .replace(
                    R.id.music_play_lyric_container,
                    FullLyricFragment(),
                    FullLyricFragment.TAG
                )
                .commit()
        }

        viewFlipper.flipTo(LYRICS_FLIP_POSITION)
    }

    fun showPlayer() {
        viewFlipper.flipTo(PLAYER_FLIP_POSITION)
    }

    fun isDragDismissInProgress(): Boolean {
        return dragDismissInProgress
    }

    fun setDismissInterceptionBlocked(blocked: Boolean) {
        dismissInterceptionBlocked = blocked
        if (!dragDismissInProgress) {
            binding.dragDismissLayout.setDisallowInterceptTouchEvent(blocked)
        }
    }

    fun ensureRecordAudioPermission(): Boolean {
        if (hasRecordAudioPermission()) {
            isRecordAudioGranted = true
            return true
        }

        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        return false
    }

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDismissed(view: View) {
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (viewFlipper.getCurrentIndex() != PLAYER_FLIP_POSITION) {
            showPlayer()
        } else {
            super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putInt(
            KEY_FLIP_POSITION,
            viewFlipper.getCurrentIndex()
        )
    }

    override fun onDestroy() {
        artworkJob?.cancel()
        artworkJob = null

        viewFlipper.release()

        super.onDestroy()
    }

    companion object {
        private const val KEY_FLIP_POSITION = "key_flip_position"

        private const val PLAYER_FLIP_POSITION = 0
        private const val LYRICS_FLIP_POSITION = 1
        private const val DEFAULT_FLIP_POSITION = PLAYER_FLIP_POSITION

        fun start(context: Context) {
            context.startActivityCompat(
                Intent(context, MusicPlayActivity::class.java)
            )
        }
    }

    private fun refreshVisiblePlayerPage() {
        (supportFragmentManager.findFragmentByTag(MusicPlayerFragment::class.java.simpleName) as? MusicPlayerFragment)
            ?.refreshFromCurrentState()
        (supportFragmentManager.findFragmentByTag(FullLyricFragment.TAG) as? FullLyricFragment)
            ?.refreshFromCurrentState()
    }
}
