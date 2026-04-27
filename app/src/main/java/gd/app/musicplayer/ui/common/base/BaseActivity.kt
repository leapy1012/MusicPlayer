package gd.app.musicplayer.ui.common.base

import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import gd.app.musicplayer.MusicPlayerApp
import gd.app.musicplayer.app.AppContainer
import gd.app.musicplayer.core.theme.ThemeObserver

abstract class BaseActivity : AppCompatActivity(), ThemeObserver {

    private var isStateSaved = false

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        super.onSaveInstanceState(outState, outPersistentState)
        isStateSaved = true
    }


    protected val appContainer: AppContainer
        get() = (application as MusicPlayerApp).appContainer

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
        applyThemeTo(findViewById(android.R.id.content))
    }

    fun applyThemeTo(root: View?) {
        appContainer.themeEngine.apply(root)
    }

    override fun onStart() {
        super.onStart()
        isStateSaved = false
        appContainer.themeRegistry.registerObserver(this)
        appContainer.themeRepo.refreshTheme(this)
    }

    override fun onRestoreInstanceState(
        savedInstanceState: Bundle?,
        persistentState: PersistableBundle?
    ) {
        super.onRestoreInstanceState(savedInstanceState, persistentState)
        isStateSaved = false
    }

    override fun onStop() {
        appContainer.themeRegistry.unregisterObserver(this)
        super.onStop()
    }

    override fun onThemeChanged(palette: gd.app.musicplayer.core.theme.ThemePalette?) {
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

    fun isStateSaved(): Boolean = isStateSaved
}
