package gd.app.musicplayer.domain.usecase.scan

import gd.app.musicplayer.data.local.preference.ScanOptionsPreferenceDataStore
import gd.app.musicplayer.ui.scan.ScanOptions
import javax.inject.Inject

class UpdateScanOptionsUseCase @Inject constructor(
    private val store: ScanOptionsPreferenceDataStore
) {
    suspend operator fun invoke(options: ScanOptions) = store.updateScanOptions(options)
}
