package gd.app.musicplayer.domain.usecase.scan

import gd.app.musicplayer.data.local.preference.ScanOptionsPreferenceDataStore
import gd.app.musicplayer.ui.feature.scan.ScanOptions
import javax.inject.Inject

class LoadScanOptionsUseCase @Inject constructor(
    private val preference: ScanOptionsPreferenceDataStore
) {
    suspend operator fun invoke(): ScanOptions = preference.getScanOptions()
}
