package gd.app.musicplayer.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.data.local.preference.SettingPreferencesDataStore
import gd.app.musicplayer.di.ApplicationScope
import gd.app.musicplayer.playback.PlaybackController
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Singleton
class ShakeDetector @Inject constructor(
    @ApplicationContext context: Context,
    private val settingPreferences: SettingPreferencesDataStore,
    private val playbackController: PlaybackController,
    @param:ApplicationScope private val applicationScope: CoroutineScope
) : SensorEventListener {

    private val appContext = context.applicationContext

    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private var sensitivityJob: Job? = null
    private var enabledJob: Job? = null

    private var isRegistered = false
    private var hasReceivedFirstSample = false

    private var lastShakeTriggeredAtMs = 0L
    private var lastSampleProcessedAtMs = 0L

    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f

    private var shakeThreshold = DEFAULT_SHAKE_THRESHOLD

    init {
        observeShakeEnabled()
        observeShakeSensitivity()
    }

    fun start() {
        if (isRegistered) {
            return
        }

        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return

        sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_NORMAL
        )

        isRegistered = true
        hasReceivedFirstSample = false
        lastSampleProcessedAtMs = 0L
    }

    fun stop() {
        if (!isRegistered) {
            return
        }

        sensorManager.unregisterListener(this)
        isRegistered = false
        hasReceivedFirstSample = false
        lastSampleProcessedAtMs = 0L
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            start()
        } else {
            stop()
        }
    }

    fun updateSensitivity(sensitivity: Float) {
        shakeThreshold =
            ((1f - sensitivity.coerceIn(0f, 1f)) * THRESHOLD_RANGE) +
                    MIN_SHAKE_THRESHOLD
    }

    override fun onSensorChanged(event: SensorEvent) {
        val values = event.values
        if (values.size < 3) return

        val now = System.currentTimeMillis()

        if (!hasReceivedFirstSample) {
            hasReceivedFirstSample = true
            lastSampleProcessedAtMs = now
            lastX = values[0]
            lastY = values[1]
            lastZ = values[2]
            return
        }

        val elapsedMs = now - lastSampleProcessedAtMs

        if (elapsedMs < MIN_SAMPLE_INTERVAL_MS) {
            return
        }

        lastSampleProcessedAtMs = now

        val deltaX = values[0] - lastX
        val deltaY = values[1] - lastY
        val deltaZ = values[2] - lastZ

        lastX = values[0]
        lastY = values[1]
        lastZ = values[2]

        val speed =
            (sqrt((deltaX * deltaX) + (deltaY * deltaY) + (deltaZ * deltaZ)) / elapsedMs) *
                    SPEED_SCALE

        if (
            speed >= shakeThreshold &&
            now - lastShakeTriggeredAtMs > MIN_SHAKE_GAP_MS &&
            playbackController.state.value.currentTrack != null
        ) {
            lastShakeTriggeredAtMs = now
            playbackController.playNext(appContext)
        }
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int
    ) = Unit

    private fun observeShakeEnabled() {
        enabledJob?.cancel()

        enabledJob = applicationScope.launch {
            setEnabled(settingPreferences.getShakeEnabled())
        }
    }

    private fun observeShakeSensitivity() {
        sensitivityJob?.cancel()

        sensitivityJob = applicationScope.launch {
            updateSensitivity(settingPreferences.getShakeLevel())
        }
    }

    companion object {
        private const val MIN_SAMPLE_INTERVAL_MS = 70L
        private const val MIN_SHAKE_GAP_MS = 1000L
        private const val SPEED_SCALE = 10000f

        private const val MIN_SHAKE_THRESHOLD = 800f
        private const val THRESHOLD_RANGE = 1400f
        private const val DEFAULT_SHAKE_THRESHOLD = 1500f
    }
}
