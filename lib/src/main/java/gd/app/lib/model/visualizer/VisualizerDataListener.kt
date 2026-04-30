package gd.app.lib.model.visualizer

interface VisualizerDataListener {
    fun onVisualizerEnabledChanged(enabled: Boolean)
    fun onFftDataChanged(magnitudes: FloatArray, waveform: FloatArray? = null)
}
