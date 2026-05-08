package gd.app.musicplayer.ui.feature.drivemode

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.data.local.preference.DrivePreferenceStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveModeLauncher @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val drivePreferenceStore: DrivePreferenceStore
) {

    suspend fun start(context: Context = appContext) {
        val showDriveWarning = drivePreferenceStore.isDriveWarningEnabled()

        if (showDriveWarning) {
            DriveRemindActivity.start(context)
        } else {
            DriveModeActivity.openDirect(context)
        }
    }
}