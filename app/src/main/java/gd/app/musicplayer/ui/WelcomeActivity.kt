package gd.app.musicplayer.ui

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.core.common.extension.isMediaOpenIntent
import gd.app.musicplayer.core.datastore.AppStartupPreferenceDataStore
import gd.app.musicplayer.core.datastore.MusicDataStore
import gd.app.musicplayer.core.datastore.SoundEffectPreferences
import gd.app.musicplayer.core.designsystem.theme.ThemeManager
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
    @Inject lateinit var themeManager: ThemeManager
    @Inject lateinit var musicDataStore: MusicDataStore
    @Inject lateinit var soundEffectPreferences: SoundEffectPreferences

    private lateinit var binding: ActivityWelcomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // Dismiss Android 12+ system splash immediately; WelcomeView is the real splash.
        val splashScreen = installSplashScreen()
        splashScreen.setOnExitAnimationListener { splashView ->
            splashView.remove()
        }

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
                // Still preload theme like original cold-start H(); skip only branding delay.
                withContext(Dispatchers.IO) {
                    themeManager.warmUp()
                    syncPreferenceMirrors()
                }
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
            runStartupWithMinimumSplashDuration()
            openMainAndFinish()
        }
    }

    /**
     * Original Welcome: [m4.c.c] / [H] on a background thread, plus DB sync.
     * Splash wall-clock only — do not wait for MediaStore → Room completion before Main.
     */
    private suspend fun runStartupWithMinimumSplashDuration() {
        val startTime = SystemClock.elapsedRealtime()

        withContext(Dispatchers.IO) {
            // Original WelcomeActivity.c Thread: f.i().k().c(appCtx) before UI proceeds.
            themeManager.warmUp()
            syncPreferenceMirrors()
            runMusicDatabaseStartupSyncUseCase()
        }

        delayRemainingSplashTime(startTime)
    }

    private suspend fun syncPreferenceMirrors() {
        musicDataStore.syncBooleansToSharedPreferences()
        soundEffectPreferences.syncBooleansToSharedPreferences()
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
        /** Branding floor only — must not include MediaStore sync time. */
        const val MIN_SPLASH_DURATION_MS = 250L
    }
}
