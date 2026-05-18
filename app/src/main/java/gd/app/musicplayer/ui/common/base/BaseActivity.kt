package gd.app.musicplayer.ui.common.base

import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import gd.app.musicplayer.core.designsystem.theme.ThemeObserver
import gd.app.musicplayer.core.designsystem.theme.ThemePalette
import gd.app.musicplayer.core.designsystem.theme.ThemeRegistry
import gd.app.musicplayer.domain.repository.ThemeRepo
import gd.app.musicplayer.ui.library.options.RingtoneActionHandler
import gd.app.musicplayer.ui.theme.ThemeEngine
import javax.inject.Inject

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
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
    }

    override fun onResume() {
        super.onResume()
        isStateSaved = false
        RingtoneActionHandler.handlePendingPermissionResult(this)
        applyThemeTo(findViewById(android.R.id.content))
    }

    fun applyThemeTo(root: View?) {
        themeEngine.apply(root)
    }

    override fun onStart() {
        super.onStart()
        isStateSaved = false
        themeRegistry.registerObserver(this)
        themeRepo.refreshTheme()
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
        themeRepo.refreshTheme()
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
