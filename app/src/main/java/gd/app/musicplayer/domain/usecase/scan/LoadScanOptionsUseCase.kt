package gd.app.musicplayer.domain.usecase.scan

import gd.app.musicplayer.core.datastore.ScanOptionsPreferenceDataStore
import gd.app.musicplayer.ui.scan.ScanOptions
import javax.inject.Inject

class LoadScanOptionsUseCase @Inject constructor(
    private val preference: ScanOptionsPreferenceDataStore
) {
    suspend operator fun invoke(): ScanOptions = preference.getScanOptions()
}
