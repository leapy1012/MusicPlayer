package gd.app.musicplayer.domain.usecase.scan

import gd.app.musicplayer.core.datastore.ScanOptionsPreferenceDataStore
import gd.app.musicplayer.domain.model.scan.ScanOptions
import javax.inject.Inject

class UpdateScanOptionsUseCase @Inject constructor(
    private val store: ScanOptionsPreferenceDataStore
) {
    suspend operator fun invoke(options: ScanOptions) = store.updateScanOptions(options)
}
