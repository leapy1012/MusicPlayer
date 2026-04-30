package gd.app.musicplayer.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import gd.app.musicplayer.playback.PlaybackControllerProvider
import kotlin.math.sqrt

class ShakeDetector private constructor(
    context: Context
) : SensorEventListener {

    private val appContext = context.applicationContext
    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private var isRegistered = false
    private var hasReceivedFirstSample = false

    private var lastShakeTriggeredAtMs = 0L
    private var lastSampleProcessedAtMs = 0L

    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f

    private var shakeThreshold = 2200f

    init {
        updateSensitivity(PreferenceUtil.getInstance(appContext).getShakeLevel())
    }

    fun start() {
        if (!isRegistered) {
            sensorManager.registerListener(
                this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL
            )
            isRegistered = true
        }
        hasReceivedFirstSample = false
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            start()
        } else {
            stop()
        }
    }

    fun updateSensitivity(sensitivity: Float) {
        shakeThreshold = ((1f - sensitivity) * 1400f) + 800f
    }

    fun stop() {
        if (isRegistered) {
            sensorManager.unregisterListener(this)
            isRegistered = false
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val values = event.values

        if (hasReceivedFirstSample) {
            val now = System.currentTimeMillis()
            val elapsedMs = now - lastSampleProcessedAtMs
            if (elapsedMs < MIN_SAMPLE_INTERVAL_MS) {
                return
            }

            lastSampleProcessedAtMs = now

            val deltaX = values[0] - lastX
            val deltaY = values[1] - lastY
            val deltaZ = values[2] - lastZ
            val speed =
                (sqrt((deltaX * deltaX) + (deltaY * deltaY) + (deltaZ * deltaZ)) / elapsedMs) *
                    SPEED_SCALE

            if (
                speed >= shakeThreshold &&
                now - lastShakeTriggeredAtMs > MIN_SHAKE_GAP_MS &&
                PlaybackControllerProvider.state.value.hasTrack
            ) {
                lastShakeTriggeredAtMs = now
                PlaybackControllerProvider.playNext(appContext)
            }
        } else {
            hasReceivedFirstSample = true
        }

        lastX = values[0]
        lastY = values[1]
        lastZ = values[2]
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        private const val MIN_SAMPLE_INTERVAL_MS = 70L
        private const val MIN_SHAKE_GAP_MS = 1000L
        private const val SPEED_SCALE = 10000f

        @Volatile
        private var instance: ShakeDetector? = null

        fun getInstance(context: Context): ShakeDetector {
            return instance ?: synchronized(this) {
                instance ?: ShakeDetector(context.applicationContext).also { instance = it }
            }
        }
    }
}

