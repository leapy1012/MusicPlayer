package gd.app.musicplayer.ui.feature.scan

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.app.AppDispatchers
import gd.app.musicplayer.domain.usecase.scan.LoadScanOptionsUseCase
import gd.app.musicplayer.domain.usecase.scan.ObserveLibraryTrackCountUseCase
import gd.app.musicplayer.domain.usecase.scan.PersistScanOptionsUseCase
import gd.app.musicplayer.domain.usecase.scan.QueryMediaStoreTracksUseCase
import gd.app.musicplayer.domain.usecase.scan.UpsertScannedTracksUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

data class ScanOptions(
    val excludeShort: Boolean = false,
    val excludeBySize: Boolean = false,
    val excludeRingtone: Boolean = false,
    val durationSec: Int = 60,
    val sizeKb: Int = 50
)

data class ScanResultSummary(
    val importedCount: Int,
    val filteredOutCount: Int,
    val addedCount: Int,
    val deletedCount: Int
)

data class ScanUiState(
    val options: ScanOptions = ScanOptions(),
    val isScanning: Boolean = false,
    val progressPercent: Int = 0,
    val currentPath: String = "",
    val result: ScanResultSummary? = null
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val loadScanOptionsUseCase: LoadScanOptionsUseCase,
    private val persistScanOptionsUseCase: PersistScanOptionsUseCase,
    private val observeLibraryTrackCountUseCase: ObserveLibraryTrackCountUseCase,
    private val queryMediaStoreTracksUseCase: QueryMediaStoreTracksUseCase,
    private val upsertScannedTracksUseCase: UpsertScannedTracksUseCase,
    private val dispatchers: AppDispatchers
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ScanUiState(options = loadSavedOptions())
    )
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    fun startScan(options: ScanOptions) {
        persistScanOptionsUseCase(options)
        scanJob?.cancel()
        _uiState.value = ScanUiState(
            options = options,
            isScanning = true,
            progressPercent = 0,
            currentPath = "",
            result = null
        )

        scanJob = viewModelScope.launch(dispatchers.io) {
            val oldCount = observeLibraryTrackCountUseCase().first()
            val imported = queryMediaStoreTracksUseCase(appContext)
            val filtered = imported.filter { track ->
                if (options.excludeShort && track.duration < options.durationSec * 1000) return@filter false
                if (options.excludeBySize && (track.size ?: 0L) < options.sizeKb * 1024L) return@filter false
                if (options.excludeRingtone && track.isRingtone != 0) return@filter false
                true
            }

            val total = max(1, filtered.size)
            var processed = 0
            val chunkSize = 500

            try {
                filtered.chunked(chunkSize).forEach { chunk ->
                    coroutineContext.ensureActive()
                    upsertScannedTracksUseCase(chunk)
                    processed += chunk.size
                    _uiState.value = _uiState.value.copy(
                        isScanning = true,
                        progressPercent = min(100, (processed * 100) / total),
                        currentPath = chunk.lastOrNull()?.data.orEmpty()
                    )
                }

                val newCount = observeLibraryTrackCountUseCase().first()
                _uiState.value = ScanUiState(
                    options = options,
                    isScanning = false,
                    progressPercent = 100,
                    currentPath = _uiState.value.currentPath,
                    result = ScanResultSummary(
                        importedCount = imported.size,
                        filteredOutCount = imported.size - filtered.size,
                        addedCount = max(0, newCount - oldCount),
                        deletedCount = max(0, imported.size - newCount)
                    )
                )
            } catch (_: CancellationException) {
                _uiState.value = ScanUiState(options = options)
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _uiState.value = ScanUiState(options = _uiState.value.options)
    }

    private fun loadSavedOptions(): ScanOptions {
        return loadScanOptionsUseCase()
    }
}
