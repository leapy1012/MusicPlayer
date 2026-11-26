package gd.app.musicplayer.ui.common.base

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import gd.app.musicplayer.core.designsystem.theme.ThemeManager
import androidx.core.view.WindowCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
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

    /** Accent ARGB last applied via [CouiAccentOverlay]; accent-only changes stay live. */
    private var appliedAccentColor: Int = 0
    private var appliedNightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    /** Whether COUI Dark chrome overlay was applied for pictured/dark themes. */
    private var appliedDarkChrome: Boolean = false
    private lateinit var initialActivityTheme: android.content.res.Resources.Theme
    private val liveThemeUpdater = gd.app.musicplayer.core.designsystem.theme.LiveThemeUpdater { applyThemeTo(it) }

    @Inject lateinit var themeEngine: ThemeEngine
    @Inject lateinit var themeRegistry: ThemeRegistry
    @Inject lateinit var themeRepo: ThemeRepo

    private var themeActivityResumed = false
    private var themeRecreationPosted = false
    protected val isThemeRecreationPending: Boolean
        get() = themeRecreationPosted || appearanceNeedsRecreation()

    private fun appearanceNeedsRecreation(): Boolean =
        requestedNightMode(themeRepo) != appliedNightMode ||
            !themeRepo.getCorePalette().isContentSurfaceLight() != appliedDarkChrome

    private val recreateForTheme = Runnable {
        themeRecreationPosted = false
        if (!isFinishing && !isDestroyed &&
            themeActivityResumed &&
            !supportFragmentManager.isStateSaved &&
            appearanceNeedsRecreation()
        ) {
            recreate()
        }
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

    private fun requestedNightMode(repo: ThemeRepo): Int =
        if (repo.getSelectedThemeType() == ThemeManager.THEME_TYPE_DARK)
            AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM

    override fun attachBaseContext(newBase: Context) {
        val repo = EntryPointAccessors.fromApplication(
            newBase.applicationContext, ActivityThemeEntryPoint::class.java
        ).themeRepo()
        appliedNightMode = requestedNightMode(repo)
        delegate.localNightMode = appliedNightMode
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // FragmentActivity restores fragments inside super.onCreate. COUI preferences
        // cache title/summary colors in their constructors, so the theme must be ready
        // before that restoration, not merely before the activity layout is inflated.
        val startupRepo = EntryPointAccessors.fromApplication(
            applicationContext, ActivityThemeEntryPoint::class.java
        ).themeRepo()
        initialActivityTheme = resources.newTheme().apply { setTo(this@BaseActivity.theme) }
        startupRepo.refreshTheme()
        applyCouiAccentOverlay(startupRepo)
        super.onCreate(savedInstanceState)
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
        themeActivityResumed = true
        // Stopped activities defer recreation until they are visible again. Recheck
        // the current palette so a dark/light round trip while stopped is a no-op.
        val palette = themeRepo.getCorePalette()
        if (isThemeRecreationPending || themeRepo.getAccentColor() != appliedAccentColor) {
            onThemeChanged(palette)
        }
        RingtoneActionHandler.handlePendingPermissionResult(this)
    }

    override fun onPause() {
        themeActivityResumed = false
        super.onPause()
    }

    fun applyThemeTo(root: View?) {
        themeEngine.apply(root)
    }

    override fun onStart() {
        super.onStart()
        // Accent updates catch up in place; neutral-theme changes rebuild on resume.
        val palette = themeRepo.getCorePalette()
        val darkChrome = !palette.isContentSurfaceLight()
        if (themeRepo.getAccentColor() != appliedAccentColor || darkChrome != appliedDarkChrome ||
            requestedNightMode(themeRepo) != appliedNightMode) {
            onThemeChanged(palette)
        }
    }

    override fun onDestroy() {
        // Original BMusicActivity.onDestroy → y.Y().I0(this)
        window.decorView.removeCallbacks(recreateForTheme)
        themeRegistry.unregisterObserver(this)
        super.onDestroy()
    }

    override fun onThemeChanged(palette: ThemePalette?) {
        val resolved = palette ?: themeRepo.getCorePalette()
        if (appearanceNeedsRecreation()) {
            // A color walk cannot reconstruct cached ColorStateLists, preference
            // decorations and dialog styles. Reinflate them through normal saved-state
            // restoration. Post outside the switch callback / fragment transaction.
            if (!themeRecreationPosted && !isFinishing && !isDestroyed &&
                themeActivityResumed &&
                !supportFragmentManager.isStateSaved
            ) {
                themeRecreationPosted = true
                window.decorView.post(recreateForTheme)
            }
            return
        }
        val changed = themeRepo.getAccentColor() != appliedAccentColor
        if (changed && ::initialActivityTheme.isInitialized) {
            liveThemeUpdater.rememberColors(theme, appliedAccentColor)
            theme.setTo(initialActivityTheme)
            applyCouiAccentOverlay()
            liveThemeUpdater.refresh(window.decorView)
            refreshFragmentThemes(supportFragmentManager)
            gd.app.musicplayer.core.designsystem.dialog.DialogRegistry.refresh(this) { dialog ->
                refreshDialogTheme(dialog)
            }
            if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) android.util.Log.d("MusicLiveTheme",
                "Updated ${javaClass.simpleName}@${System.identityHashCode(this)} dark=$appliedDarkChrome")
        }
        applySystemBarAppearance(resolved)
        applyThemeTo(findViewById(android.R.id.content))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Ensure palette follows system night if type must change; notify only when swapped.
        themeRepo.refreshTheme()
        val palette = themeRepo.getCorePalette()
        onThemeChanged(palette)
    }

    private fun refreshFragmentThemes(manager: androidx.fragment.app.FragmentManager) {
        manager.fragments.forEach { fragment ->
            if (fragment is androidx.fragment.app.DialogFragment) fragment.dialog?.let(::refreshDialogTheme)
            if (fragment is androidx.preference.PreferenceFragmentCompat) {
                // Rebind theme-derived row decorations; RecyclerView retains its position.
                fragment.listView?.adapter?.notifyDataSetChanged()
            }
            refreshFragmentThemes(fragment.childFragmentManager)
        }
    }

    private fun refreshDialogTheme(dialog: android.app.Dialog) {
        liveThemeUpdater.refreshContext(dialog.context)
        if (dialog is com.coui.appcompat.panel.COUIBottomSheetDialog) dialog.refresh()
        dialog.window?.decorView?.let(liveThemeUpdater::refresh)
        if (dialog is androidx.appcompat.app.AlertDialog) {
            gd.app.musicplayer.core.designsystem.dialog.CouiAlertDialogSurface.refresh(dialog)
        }
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

    private fun applyCouiAccentOverlay(repo: ThemeRepo = themeRepo) {
        val palette = repo.getCorePalette()
        appliedDarkChrome = CouiAccentOverlay.applyDarkChromeIfNeeded(this, palette)
        val accent = repo.getAccentColor()
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

/** Available before Activity injection and FragmentActivity saved-state restoration. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ActivityThemeEntryPoint {
    fun themeRepo(): ThemeRepo
}
