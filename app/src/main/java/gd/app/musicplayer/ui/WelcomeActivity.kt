package gd.app.musicplayer.ui

import android.os.Bundle
import android.os.SystemClock
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.databinding.ActivityWelcomeBinding
import gd.app.musicplayer.domain.usecase.database.RunMusicDatabaseStartupSyncUseCase
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.shell.MainActivity
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class WelcomeActivity : BaseActivity() {

    private companion object {
        const val MIN_SPLASH_DURATION_MS = 250L
    }

    private lateinit var binding: ActivityWelcomeBinding

    @Inject lateinit var runMusicDatabaseStartupSyncUseCase: RunMusicDatabaseStartupSyncUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        checkPermissions()
    }

    private fun checkPermissions() {

        if (hasAudioPermission()) {
            onAudioPermissionGranted()
        } else {
            requestAudioPermission()
        }
    }

    override fun onAudioPermissionGranted() {

        super.onAudioPermissionGranted()
        requestNotificationPermission()
    }

    override fun onNotificationPermissionResult() {
        super.onNotificationPermissionResult()
        startLoading()
    }

    private fun startLoading() {

        lifecycleScope.launch {

            val startTime = SystemClock.elapsedRealtime()

            withContext(Dispatchers.IO) {
                runMusicDatabaseStartupSyncUseCase()
            }

            val elapsed = SystemClock.elapsedRealtime() - startTime
            val delayTime = (MIN_SPLASH_DURATION_MS - elapsed).coerceAtLeast(0)
            delay(delayTime)

            onDataReady()
        }
    }

    private fun onDataReady() {
        MainActivity.start(this)
        finish()
    }
}
