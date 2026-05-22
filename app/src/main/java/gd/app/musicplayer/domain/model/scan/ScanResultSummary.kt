package gd.app.musicplayer.domain.model.scan

data class ScanResultSummary(
    val importedCount: Int,
    val filteredOutCount: Int,
    val addedCount: Int,
    val deletedCount: Int,
    val hiddenCount: Int = 0,
    val libraryInfo: ScanLibraryInfo = ScanLibraryInfo()
)
