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
import androidx.core.view.WindowCompat
import gd.app.musicplayer.R
import gd.app.musicplayer.core.designsystem.theme.CouiAccentOverlay
import gd.app.musicplayer.core.designsystem.theme.ThemeObserver
import gd.app.musicplayer.core.designsystem.theme.ThemePalette
import gd.app.musicplayer.core.designsystem.theme.ThemeRegistry
import gd.app.musicplayer.domain.repository.ThemeRepo
import gd.app.musicplayer.feature.library.options.RingtoneActionHandler
import gd.app.musicplayer.ui.theme.ThemeEngine
import javax.inject.Inject

/**
 * Theme lifecycle matches original [BaseActivity.K0] / [BMusicActivity.onDestroy]:
 * register observer + apply once after content inflate; stay registered while stopped
 * so ThemeActivity notify still rethemes back-stack activities; unregister in [onDestroy].
 * Retheme only on [onThemeChanged] (and configuration when system night forces a type change).
 *
 * Transitions match original [BMusicActivity.startActivityForResult] / [finish]:
 * always [overridePendingTransition] with music_activity_in/out (not theme-only).
 */
abstract class BaseActivity : AppCompatActivity(), ThemeObserver {

    private var isStateSaved = false
    /** Accent ARGB last applied via [CouiAccentOverlay]; used to recreate when it changes. */
    private var appliedAccentColor: Int = 0
    /** Whether COUI Dark chrome overlay was applied for pictured/dark themes. */
    private var appliedDarkChrome: Boolean = false

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
        // Exact accent → ResourcesLoader + ThemeOverlay.App.Accent before inflate so
        // COUI switches/tabs/prefs resolve couiColorPrimary* to the picker color.
        applyCouiAccentOverlay()
        // Match icon contrast to header chrome (picture/dark → light icons).
        // SystemBarStyle.auto + AppTheme windowLightStatusBar=true left black icons
        // on teal pictured headers.
        applySystemBarAppearance(themeRepo.getCorePalette())
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
        // Accent / chrome mode may have changed in another activity; recreate so
        // CouiAccentOverlay reinstalls before the next inflate.
        val palette = themeRepo.getCorePalette()
        val darkChrome = !palette.isContentSurfaceLight()
        if (themeRepo.getAccentColor() != appliedAccentColor || darkChrome != appliedDarkChrome) {
            recreate()
        }
    }

    override fun onRestoreInstanceState(
        savedInstanceState: Bundle?,
        persistentState: PersistableBundle?
    ) {
        super.onRestoreInstanceState(savedInstanceState, persistentState)
        isStateSaved = false
    }

    override fun onDestroy() {
        // Original BMusicActivity.onDestroy → y.Y().I0(this)
        themeRegistry.unregisterObserver(this)
        super.onDestroy()
    }

    override fun onThemeChanged(palette: ThemePalette?) {
        val resolved = palette ?: themeRepo.getCorePalette()
        // Dark COUI overlay is inflate-time only — recreate when chrome mode flips.
        if (!resolved.isContentSurfaceLight() != appliedDarkChrome) {
            recreate()
            return
        }
        applySystemBarAppearance(resolved)
        applyThemeTo(findViewById(android.R.id.content))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Ensure palette follows system night if type must change; notify only when swapped.
        themeRepo.refreshTheme()
        val palette = themeRepo.getCorePalette()
        applySystemBarAppearance(palette)
        applyThemeTo(findViewById(android.R.id.content))
    }

    /**
     * Original K0: register observer (y.Y().L) + apply current palette (i).
     */
    private fun applyThemeAfterContentSet() {
        themeRegistry.registerObserver(this)
        applySystemBarAppearance(themeRepo.getCorePalette())
        applyThemeTo(findViewById(android.R.id.content))
    }

    /**
     * Light status/nav icons when the header (and mini-player) chrome is dark —
     * pictured and dark themes. Light theme keeps dark icons for white surfaces.
     * Subclasses with always-dark chrome (e.g. full player) may force dark bars.
     */
    protected open fun prefersLightSystemBars(palette: ThemePalette): Boolean {
        return palette.isHeaderSurfaceLight()
    }

    protected fun refreshSystemBarAppearance() {
        applySystemBarAppearance(themeRepo.getCorePalette())
    }

    private fun applySystemBarAppearance(palette: ThemePalette) {
        val lightBars = prefersLightSystemBars(palette)
        enableEdgeToEdge(
            statusBarStyle = if (lightBars) {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            } else {
                SystemBarStyle.dark(Color.TRANSPARENT)
            },
            navigationBarStyle = if (lightBars) {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            } else {
                SystemBarStyle.dark(Color.TRANSPARENT)
            },
        )
        // AppTheme still declares an opaque navigationBarColor; OEMs (and theme
        // re-inflate) can restore it after enableEdgeToEdge. Force transparent so
        // the player / sheet plate can paint behind the 3-button icons — same as
        // BaseBottomSheetDialogFragment.applyNavigationChrome.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
    }

    private fun applyCouiAccentOverlay() {
        val palette = themeRepo.getCorePalette()
        appliedDarkChrome = CouiAccentOverlay.applyDarkChromeIfNeeded(this, palette)
        val accent = themeRepo.getAccentColor()
        CouiAccentOverlay.apply(this, accent)
        appliedAccentColor = accent
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
