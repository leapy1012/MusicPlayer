package gd.app.musicplayer.ui.common.base

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import gd.app.musicplayer.R
import gd.app.musicplayer.core.designsystem.theme.ThemeObserver
import gd.app.musicplayer.core.designsystem.theme.ThemePalette
import gd.app.musicplayer.core.designsystem.theme.ThemeRegistry
import gd.app.musicplayer.domain.repository.ThemeRepo
import gd.app.musicplayer.feature.library.options.RingtoneActionHandler
import gd.app.musicplayer.ui.theme.ThemeEngine
import javax.inject.Inject

/**
 * Theme lifecycle matches original [BaseActivity.K0] / [BMusicActivity.i]:
 * register observer + apply once after content inflate; retheme only on [onThemeChanged]
 * (and configuration when system night forces a palette type change).
 *
 * Transitions match original [BMusicActivity.startActivityForResult] / [finish]:
 * always [overridePendingTransition] with music_activity_in/out (not theme-only).
 */
abstract class BaseActivity : AppCompatActivity(), ThemeObserver {

    private var isStateSaved = false

    @Inject lateinit var themeEngine: ThemeEngine
    @Inject lateinit var themeRegistry: ThemeRegistry
    @Inject lateinit var themeRepo: ThemeRepo

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        super.onSaveInstanceState(outState, outPersistentState)
        isStateSaved = true
    }

    companion object {
        val AUDIO_PERMISSIONS =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO)
            else
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        private const val NOTIFICATION_PERMISSION = android.Manifest.permission.POST_NOTIFICATIONS
    }
    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->

            if (result.isEmpty()) {
                return@registerForActivityResult
            }

            if (hasAudioPermission()) {
                onAudioPermissionGranted()
            } else {
                finish()
            }
        }
    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            onNotificationPermissionResult()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Original [BMusicActivity.T0] → w0.c(this, false, true): transparent system bars
        // before content. Dream uses enableEdgeToEdge for the same window flags.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
    }

    /**
     * Original [BMusicActivity.startActivityForResult]: after start, always force
     * music_activity_in / music_activity_out. Theme windowAnimationStyle alone is not
     * what the APK relies on for the open path from Main/More.
     */
    override fun startActivity(intent: Intent, options: Bundle?) {
        super.startActivity(intent, options)
        overridePendingTransition(R.anim.music_activity_in, R.anim.music_activity_out)
    }

    @Deprecated("Deprecated in Java")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        @Suppress("DEPRECATION")
        super.startActivityForResult(intent, requestCode, options)
        overridePendingTransition(R.anim.music_activity_in, R.anim.music_activity_out)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.music_activity_in, R.anim.music_activity_out)
    }

    /**
     * Original [BActivity.O0] → [K0]: theme walk runs once after [setContentView].
     */
    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        applyThemeAfterContentSet()
    }

    override fun setContentView(view: View?) {
        super.setContentView(view)
        applyThemeAfterContentSet()
    }

    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        super.setContentView(view, params)
        applyThemeAfterContentSet()
    }

    override fun onResume() {
        super.onResume()
        isStateSaved = false
        RingtoneActionHandler.handlePendingPermissionResult(this)
        // Original does not retheme on resume.
    }

    fun applyThemeTo(root: View?) {
        themeEngine.apply(root)
    }

    override fun onStart() {
        super.onStart()
        isStateSaved = false
        // Original y.Y().L(this) — register only; no refreshTheme / c().
        themeRegistry.registerObserver(this)
    }

    override fun onRestoreInstanceState(
        savedInstanceState: Bundle?,
        persistentState: PersistableBundle?
    ) {
        super.onRestoreInstanceState(savedInstanceState, persistentState)
        isStateSaved = false
    }

    override fun onStop() {
        themeRegistry.unregisterObserver(this)
        super.onStop()
    }

    override fun onThemeChanged(palette: ThemePalette?) {
        applyThemeTo(findViewById(android.R.id.content))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Ensure palette follows system night if type must change; notify only when swapped.
        themeRepo.refreshTheme()
        applyThemeTo(findViewById(android.R.id.content))
    }

    private fun applyThemeAfterContentSet() {
        applyThemeTo(findViewById(android.R.id.content))
    }

    protected open fun onAudioPermissionGranted() {}
    protected open fun onNotificationPermissionResult() {}
    protected fun hasAudioPermission(): Boolean {

        return AUDIO_PERMISSIONS.all {
            checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
    }

    protected fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(NOTIFICATION_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    protected fun requestAudioPermission() {
        permissionLauncher.launch(AUDIO_PERMISSIONS)
    }

    protected fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
            notificationPermissionLauncher.launch(NOTIFICATION_PERMISSION)
        } else {
            onNotificationPermissionResult()
        }
    }
}
