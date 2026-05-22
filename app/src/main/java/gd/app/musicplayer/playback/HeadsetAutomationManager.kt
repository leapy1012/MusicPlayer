package gd.app.musicplayer.playback

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import gd.app.musicplayer.core.datastore.HeadsetSettingPreference
import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import gd.app.musicplayer.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach


@Singleton
class HeadsetAutomationManager @Inject constructor(
    private val playbackController: PlaybackController,
    settingPreferencesDataStore: SettingPreferencesDataStore,
    @ApplicationScope applicationScope: CoroutineScope
) {
    @Volatile
    private var registered = false
    private var lastRegisterAtMs = 0L
    private var wiredHeadsetOn = false
    private var bluetoothHeadsetOn = false

    @Volatile
    private var headsetSettings = HeadsetSettingPreference()

    init {
        settingPreferencesDataStore.observeSettingPreferences()
            .map { preferences -> preferences.headset }
            .distinctUntilChanged()
            .onEach { settings -> headsetSettings = settings }
            .launchIn(applicationScope)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_HEADSET_PLUG -> handleWiredState(
                    context,
                    intent.getIntExtra("state", -1) == 1
                )
                AudioManagerActions.ACTION_AUDIO_BECOMING_NOISY -> handleBecomingNoisy(context)
                BluetoothDevice.ACTION_ACL_CONNECTED -> handleBluetoothState(
                    context,
                    intent,
                    connected = true
                )
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> handleBluetoothState(
                    context,
                    intent,
                    connected = false
                )
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    when (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)) {
                        BluetoothProfile.STATE_CONNECTED -> handleBluetoothState(
                            context,
                            intent,
                            connected = true
                        )
                        BluetoothProfile.STATE_DISCONNECTED -> handleBluetoothState(
                            context,
                            intent,
                            connected = false
                        )
                    }
                }
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                    when (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)) {
                        BluetoothProfile.STATE_CONNECTED -> handleBluetoothState(
                            context,
                            intent,
                            connected = true
                        )
                        BluetoothProfile.STATE_DISCONNECTED -> handleBluetoothState(
                            context,
                            intent,
                            connected = false
                        )
                    }
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF &&
                        bluetoothHeadsetOn
                    ) {
                        bluetoothHeadsetOn = false
                        maybeStopForBluetoothDisconnect(context)
                    }
                }
            }
        }
    }

    fun initialize(context: Context) {
        if (registered) return
        synchronized(this) {
            if (registered) return
            val appContext = context.applicationContext
            bluetoothHeadsetOn = isBluetoothHeadsetConnected(appContext)
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_HEADSET_PLUG)
                addAction(AudioManagerActions.ACTION_AUDIO_BECOMING_NOISY)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            lastRegisterAtMs = SystemClock.elapsedRealtime()
            registered = true
        }
    }

    private fun handleWiredState(context: Context, connected: Boolean) {
        wiredHeadsetOn = connected
        if (SystemClock.elapsedRealtime() - lastRegisterAtMs < NOISY_GUARD_MS) return
        val settings = headsetSettings
        if (connected) {
            if (settings.headsetInPlayEnabled) {
                playbackController.play()
            }
        } else if (settings.headsetOutStopEnabled) {
            playbackController.pause()
        }
    }

    private fun handleBecomingNoisy(context: Context) {
        val settings = headsetSettings
        if (wiredHeadsetOn) {
            handleWiredState(context, connected = false)
            return
        }
        if (bluetoothHeadsetOn && settings.bluetoothAutoStopEnabled) {
            playbackController.pause()
        } else if (settings.headsetOutStopEnabled) {
            playbackController.pause()
        }
    }

    private fun handleBluetoothState(context: Context, intent: Intent, connected: Boolean) {

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val device = intent.parcelableExtraCompat<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
        if (!isAudioDevice(device.bluetoothClass)) return
        bluetoothHeadsetOn = connected
        val settings = headsetSettings
        if (connected) {
            if (settings.bluetoothAutoStartEnabled) {
                playbackController.play()
            }
        } else {
            maybeStopForBluetoothDisconnect(context)
        }
    }

    private fun maybeStopForBluetoothDisconnect(context: Context) {
        if (headsetSettings.bluetoothAutoStopEnabled) {
            playbackController.pause()
        }
    }

    private fun isBluetoothHeadsetConnected(context: Context): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return runCatching {
            adapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothAdapter.STATE_CONNECTED ||
                adapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothAdapter.STATE_CONNECTED
        }.getOrDefault(false)
    }

    private fun isAudioDevice(bluetoothClass: BluetoothClass?): Boolean {
        val majorClass = bluetoothClass?.majorDeviceClass ?: return false
        return majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO
    }

    private object AudioManagerActions {
        const val ACTION_AUDIO_BECOMING_NOISY = "android.media.AUDIO_BECOMING_NOISY"
    }

    @Suppress("DEPRECATION")
    private inline fun <reified T> Intent.parcelableExtraCompat(name: String): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(name, T::class.java)
        } else {
            getParcelableExtra(name)
        }

    private companion object {
        const val NOISY_GUARD_MS = 2_000L
    }
}
