package gd.app.musicplayer.playback

import android.Manifest
import android.bluetooth.BluetoothAdapter
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
import gd.app.musicplayer.util.PreferenceUtil

object HeadsetAutomationManager {
    private const val NOISY_GUARD_MS = 2_000L

    @Volatile
    private var registered = false
    private var lastRegisterAtMs = 0L
    private var wiredHeadsetOn = false
    private var bluetoothHeadsetOn = false

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
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            lastRegisterAtMs = SystemClock.elapsedRealtime()
            registered = true
        }
    }

    private fun handleWiredState(context: Context, connected: Boolean) {
        wiredHeadsetOn = connected
        if (SystemClock.elapsedRealtime() - lastRegisterAtMs < NOISY_GUARD_MS) return
        val preferences = PreferenceUtil.getInstance(context)
        if (connected) {
            if (preferences.shouldPlayWhenHeadsetConnected()) {
                PlaybackGateway.play(context)
            }
        } else if (preferences.shouldStopWhenHeadsetDisconnected()) {
            PlaybackGateway.pause(context)
        }
    }

    private fun handleBecomingNoisy(context: Context) {
        val preferences = PreferenceUtil.getInstance(context)
        if (wiredHeadsetOn) {
            handleWiredState(context, connected = false)
            return
        }
        if (bluetoothHeadsetOn && preferences.isBluetoothAutoStopEnabled()) {
            PlaybackGateway.pause(context)
        } else if (preferences.shouldStopWhenHeadsetDisconnected()) {
            PlaybackGateway.pause(context)
        }
    }

    private fun handleBluetoothState(context: Context, intent: Intent, connected: Boolean) {
        val device = intent.parcelableExtraCompat<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
        if (!isAudioDevice(device.bluetoothClass)) return
        bluetoothHeadsetOn = connected
        val preferences = PreferenceUtil.getInstance(context)
        if (connected) {
            if (preferences.isBluetoothAutoStartEnabled()) {
                PlaybackGateway.play(context)
            }
        } else {
            maybeStopForBluetoothDisconnect(context)
        }
    }

    private fun maybeStopForBluetoothDisconnect(context: Context) {
        if (PreferenceUtil.getInstance(context).isBluetoothAutoStopEnabled()) {
            PlaybackGateway.pause(context)
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
        if (majorClass != BluetoothClass.Device.Major.AUDIO_VIDEO) return false
        return when (bluetoothClass.deviceClass) {
            BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
            BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
            BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
            BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO -> true
            else -> false
        }
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
}

