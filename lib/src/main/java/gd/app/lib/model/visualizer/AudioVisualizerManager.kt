package gd.app.lib.model.visualizer

import android.media.audiofx.Visualizer
import android.util.Log
import java.util.concurrent.Executors

object AudioVisualizerManager {

    private const val TAG = "AudioVisualizerManager"

    private var userEnabled = false
    private var playbackActive = false
    private var useRealAudioData = false
    private var audioSessionId = -1

    private var visualizer: Visualizer? = null
    private val lock = Any()
    private val listeners = mutableListOf<VisualizerDataListener>()
    private val executor = Executors.newSingleThreadExecutor()
    private val fftProcessor = FftMagnitudeProcessor()

    private val captureListener = object : Visualizer.OnDataCaptureListener {
        override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
            if (!userEnabled || !playbackActive || fft == null) return

            if (!useRealAudioData) {
                fft.fill(0)
            }

            val magnitudes = fftProcessor.process(fft) ?: return

            listeners.toList().forEach { listener ->
                listener.onFftDataChanged(magnitudes, null)
            }
        }

        override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit
    }

    fun addListener(listener: VisualizerDataListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: VisualizerDataListener) {
        listeners.remove(listener)
    }

    fun setUserEnabled(enabled: Boolean) {
        userEnabled = enabled
        refresh()
    }

    fun setPlaybackActive(active: Boolean) {
        playbackActive = active
        refresh()
    }

    fun setUseRealAudioData(enabled: Boolean) {
        useRealAudioData = enabled
    }

    fun setAudioSessionId(sessionId: Int) {
        if (audioSessionId != sessionId) {
            release()
        }
        audioSessionId = sessionId
        refresh()
    }

    private fun canOpen(): Boolean = userEnabled && playbackActive && audioSessionId != -1

    private fun refresh() {
        if (canOpen()) {
            open()
            notifyEnabledChanged(true)
        } else {
            release()
            notifyEnabledChanged(false)
        }
    }

    private fun notifyEnabledChanged(enabled: Boolean) {
        listeners.toList().forEach { listener ->
            listener.onVisualizerEnabledChanged(enabled)
        }
    }

    private fun open() {
        executor.execute {
            synchronized(lock) {
                if (visualizer != null || audioSessionId == -1) return@synchronized

                try {
                    visualizer = Visualizer(audioSessionId).apply {
                        captureSize = Visualizer.getCaptureSizeRange()[1]
                        setDataCaptureListener(
                            captureListener,
                            Visualizer.getMaxCaptureRate() * 3 / 4,
                            false,
                            true
                        )
                        scalingMode = Visualizer.SCALING_MODE_NORMALIZED
                        enabled = true
                    }
                } catch (error: Throwable) {
                    Log.e(TAG, "Failed to open visualizer", error)
                    releaseInternal()
                }
            }
        }
    }

    fun release() {
        executor.execute {
            releaseInternal()
        }
    }

    private fun releaseInternal() {
        synchronized(lock) {
            val current = visualizer ?: return

            try {
                current.enabled = false
            } catch (error: Throwable) {
                Log.w(TAG, "Failed to disable visualizer", error)
            }

            try {
                current.release()
            } catch (error: Throwable) {
                Log.w(TAG, "Failed to release visualizer", error)
            } finally {
                visualizer = null
            }
        }
    }
}
