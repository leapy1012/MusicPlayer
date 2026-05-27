package gd.app.musicplayer.ui

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.core.common.extension.isMediaOpenIntent
import gd.app.musicplayer.core.datastore.AppStartupPreferenceDataStore
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

    @Inject lateinit var runMusicDatabaseStartupSyncUseCase: RunMusicDatabaseStartupSyncUseCase
    @Inject lateinit var appStartupPreferenceDataStore: AppStartupPreferenceDataStore

    private lateinit var binding: ActivityWelcomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configureSystemBars()
        handleStartup()
    }

    private fun configureSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    private fun handleStartup() {
        lifecycleScope.launch {
            if (shouldBypassStartupForIncomingIntent()) {
                openMainAndFinish()
                return@launch
            }

            checkAudioPermission()
        }
    }

    private fun checkAudioPermission() {
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
            runStartupSyncWithMinimumSplashDuration()
            openMainAndFinish()
        }
    }

    private suspend fun runStartupSyncWithMinimumSplashDuration() {
        val startTime = SystemClock.elapsedRealtime()

        withContext(Dispatchers.IO) {
            runMusicDatabaseStartupSyncUseCase()
        }

        delayRemainingSplashTime(startTime)
    }

    private suspend fun delayRemainingSplashTime(startTimeMillis: Long) {
        val elapsedMillis = SystemClock.elapsedRealtime() - startTimeMillis
        val remainingMillis = (MIN_SPLASH_DURATION_MS - elapsedMillis).coerceAtLeast(0L)

        delay(remainingMillis)
    }

    private fun openMainAndFinish() {
        MainActivity.start(
            context = this,
            sourceIntent = intent
        )
        finish()
    }

    private suspend fun shouldBypassStartupForIncomingIntent(): Boolean {
        val hasCompletedInitialStartup = !appStartupPreferenceDataStore.isFirstStart()
        return hasCompletedInitialStartup && intent.isMediaOpenIntent()
    }

    private companion object {
        const val MIN_SPLASH_DURATION_MS = 250L
    }
}