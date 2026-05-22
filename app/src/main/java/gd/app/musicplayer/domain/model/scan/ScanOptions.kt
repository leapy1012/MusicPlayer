package gd.app.musicplayer.domain.model.scan

data class ScanOptions(
    val excludeBySeconds: Boolean = false,
    val excludeBySize: Boolean = false,
    val excludeRingtone: Boolean = false,
    val excludeSeconds: Long = 60L,
    val excludeSizeKb: Long = 50L,
    val selectedScanPaths: List<String> = emptyList()
)
