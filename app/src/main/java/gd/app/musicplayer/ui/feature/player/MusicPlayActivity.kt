package gd.app.musicplayer.ui.feature.player

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import gd.app.lib.view.DragDismissLayout
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.loadBlurredArtworkBackground
import gd.app.musicplayer.core.extension.startActivityCompat
import gd.app.musicplayer.databinding.ActivityMusicplayBinding
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.model.ViewFlipHelper
import gd.app.musicplayer.ui.feature.lyrics.FullLyricFragment
import gd.app.musicplayer.ui.player.PlayerViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MusicPlayActivity : BaseActivity(), DragDismissLayout.OnDismissListener {

    private val playbackViewModel: PlayerViewModel by viewModels()
    private lateinit var binding: ActivityMusicplayBinding
    private lateinit var backgroundImage: View
    private val viewFlipper = ViewFlipHelper()
    private var playbackJob: Job? = null
    private var isRecordAudioGranted = false
    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            isRecordAudioGranted = granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMusicplayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        setupDragDismiss()
        setupViewFlipper(savedInstanceState)
        setupInitialFragment(savedInstanceState)
        isRecordAudioGranted = hasRecordAudioPermission()
        applyCurrentArtwork()
        observePlaybackArtwork()
    }

    private fun setupViews() {
        backgroundImage = binding.musicPlaySkin
    }

    private fun setupDragDismiss() {
        binding.dragDismissLayout.apply {
            setAllowedDirections(DragDismissLayout.Direction.DOWN)
            setOnDismissListener(this@MusicPlayActivity)
        }
    }

    private fun setupViewFlipper(savedInstanceState: Bundle?) {
        viewFlipper.bindViews(
            binding.root,
            R.id.music_play_content,
            R.id.music_play_lyric_container
        )

        val restoredIndex = savedInstanceState?.getInt(KEY_FLIP_POSITION, 0) ?: 0
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

    private fun applyCurrentArtwork() {
//        binding.musicPlaySkin.loadBlurredArtworkBackground(playbackViewModel.playbackState.value.currentTrack?.albumPicture)
    }

    private fun observePlaybackArtwork() {
//        playbackJob?.cancel()
//        playbackJob = lifecycleScope.launch {
//            playbackViewModel.playbackState.collect { state ->
//                binding.musicPlaySkin.loadBlurredArtworkBackground(state.currentTrack?.albumPicture)
//            }
//        }
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
        viewFlipper.flipTo(1)
    }

    fun showPlayer() {
        viewFlipper.flipTo(0)
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

    override fun onBackPressed() {
        if (viewFlipper.getCurrentIndex() != 0) {
            showPlayer()
        } else {
            super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_FLIP_POSITION, viewFlipper.getCurrentIndex())
    }

    override fun onDestroy() {
        playbackJob?.cancel()
        playbackJob = null
        viewFlipper.release()
        super.onDestroy()
    }
    companion object {
        private const val KEY_FLIP_POSITION = "key_flip_position"

        fun start(context: Context) {
            context.startActivityCompat(
                Intent(context, MusicPlayActivity::class.java)
            )
        }
    }
}
