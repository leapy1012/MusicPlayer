package gd.app.musicplayer.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.core.common.dispatcher.AppDispatchers
import gd.app.musicplayer.domain.usecase.scan.GetScanLibrarySummaryUseCase
import gd.app.musicplayer.domain.usecase.scan.LoadScanOptionsUseCase
import gd.app.musicplayer.domain.usecase.scan.ScanAudioFilesUseCase
import gd.app.musicplayer.domain.usecase.scan.SyncMediaStoreLibraryUseCase
import gd.app.musicplayer.domain.usecase.scan.UpdateScanOptionsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ScanPhase {
    Idle,
    Scanning,
    Result
}

enum class ScanStep {
    FindingFiles,
    ParsingFiles,
    WritingDatabase
}

data class ScanOptions(
    val excludeBySeconds: Boolean = false,
    val excludeBySize: Boolean = false,
    val excludeRingtone: Boolean = false,
    val excludeSeconds: Long = 60L,
    val excludeSizeKb: Long = 50L,
    val selectedScanPaths: List<String> = emptyList()
)

data class ScanResultSummary(
    val importedCount: Int,
    val filteredOutCount: Int,
    val addedCount: Int,
    val deletedCount: Int,
    val hiddenCount: Int = 0,
    val libraryInfo: ScanLibraryInfo = ScanLibraryInfo()
)

data class ScanLibraryInfo(
    val songs: Int = 0,
    val albums: Int = 0,
    val artists: Int = 0
)

data class ScanUiState(
    val phase: ScanPhase = ScanPhase.Idle,
    val options: ScanOptions = ScanOptions(),
    val libraryInfo: ScanLibraryInfo = ScanLibraryInfo(),
    val step: ScanStep = ScanStep.FindingFiles,
    val progressPercent: Int = 0,
    val currentPath: String = "",
    val result: ScanResultSummary? = null
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val getScanLibrarySummaryUseCase: GetScanLibrarySummaryUseCase,
    private val loadScanOptionsUseCase: LoadScanOptionsUseCase,
    private val updateScanOptionsUseCase: UpdateScanOptionsUseCase,
    private val scanAudioFilesUseCase: ScanAudioFilesUseCase,
    private val syncMediaStoreLibraryUseCase: SyncMediaStoreLibraryUseCase,
    private val dispatchers: AppDispatchers
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    init {
        viewModelScope.launch(dispatchers.io) {
            val options = loadScanOptionsUseCase()
            val libraryInfo = getScanLibrarySummaryUseCase()
            _uiState.update { state ->
                state.copy(
                    options = options,
                    libraryInfo = libraryInfo
                )
            }
        }
    }

    fun startScan(options: ScanOptions) {
        scanJob?.cancel()

        scanJob = viewModelScope.launch(dispatchers.io) {
            _uiState.value = ScanUiState(
                phase = ScanPhase.Scanning,
                options = options,
                libraryInfo = _uiState.value.libraryInfo
            )

            try {
                updateScanOptionsUseCase(options)

                scanAudioFilesUseCase(
                    selectedPaths = options.selectedScanPaths,
                    onFindingFile = { path ->
                        updateScanningState(
                            options = options,
                            step = ScanStep.FindingFiles,
                            currentPath = path
                        )
                    },
                    onParseProgress = { progress ->
                        updateScanningState(
                            options = options,
                            step = ScanStep.ParsingFiles,
                            progressPercent = progress
                        )
                    }
                )

                updateScanningState(
                    options = options,
                    step = ScanStep.WritingDatabase
                )

                val result = syncMediaStoreLibraryUseCase(options)

                _uiState.value = ScanUiState(
                    phase = ScanPhase.Result,
                    options = options,
                    libraryInfo = result.libraryInfo,
                    progressPercent = 100,
                    currentPath = _uiState.value.currentPath,
                    result = result
                )
            } catch (_: CancellationException) {
                resetToIdle(options)
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        resetToIdle(_uiState.value.options)
    }

    private fun updateScanningState(
        options: ScanOptions,
        step: ScanStep,
        progressPercent: Int = _uiState.value.progressPercent,
        currentPath: String = _uiState.value.currentPath
    ) {
        _uiState.value = ScanUiState(
            phase = ScanPhase.Scanning,
            options = options,
            libraryInfo = _uiState.value.libraryInfo,
            step = step,
            progressPercent = progressPercent.coerceIn(0, 100),
            currentPath = currentPath
        )
    }

    private fun resetToIdle(options: ScanOptions) {
        _uiState.value = ScanUiState(
            phase = ScanPhase.Idle,
            options = options,
            libraryInfo = _uiState.value.libraryInfo
        )
    }

    override fun onCleared() {
        scanJob?.cancel()
        super.onCleared()
    }

    companion object {
    }
}
