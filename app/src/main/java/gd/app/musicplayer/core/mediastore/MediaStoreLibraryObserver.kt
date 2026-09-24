package gd.app.musicplayer.core.mediastore

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.core.common.dispatcher.AppDispatchers
import gd.app.musicplayer.di.ApplicationScope
import gd.app.musicplayer.domain.usecase.scan.SyncMediaStoreLibraryUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * MediaStore → Room sync scheduler.
 *
 * Matches Music Player 8.1.5 [v5.m]: schedule-only, single background worker,
 * coalesce + up-to-6s spacing. Never blocks Welcome → Main.
 */
@Singleton
class MediaStoreLibraryObserver @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val syncMediaStoreLibraryUseCase: SyncMediaStoreLibraryUseCase,
    private val dispatchers: AppDispatchers
) {

    private val handler = Handler(Looper.getMainLooper())
    private val syncRunnable = Runnable { runSync() }
    private var lastScheduleTime = 0L
    private var registered = false
    private var syncScheduled = false
    private var syncJob: Job? = null
    private var syncPendingWhileRunning = false
    private val observedUris by lazy(::buildObservedUris)

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            // Original v5.d: ignore selfChange notifications.
            if (!selfChange) {
                scheduleSync()
            }
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            onChange(selfChange)
        }

        override fun onChange(selfChange: Boolean, uri: Uri?, flags: Int) {
            onChange(selfChange, uri)
        }
    }

    /**
     * Register ContentObservers only. Original registers on foreground and
     * schedules sync separately from [BMusicActivity.d1] / Welcome — do not
     * auto-sync here or cold start races a second full scan.
     */
    fun register() {
        if (registered) return
        registered = true
        observedUris.forEach { uri ->
            appContext.contentResolver.registerContentObserver(uri, true, observer)
        }
    }

    fun unregister() {
        if (!registered) return
        registered = false
        handler.removeCallbacks(syncRunnable)
        syncScheduled = false
        appContext.contentResolver.unregisterContentObserver(observer)
    }

    /**
     * Original [v5.m.f]: fire-and-forget schedule. Safe to call from UI.
     */
    fun scheduleSync() {
        if (syncJob?.isActive == true) {
            syncPendingWhileRunning = true
            return
        }
        if (syncScheduled) return
        syncScheduled = true
        val now = SystemClock.elapsedRealtime()
        val delay = (SYNC_DEBOUNCE_MS - (now - lastScheduleTime)).coerceAtLeast(0L)
        lastScheduleTime = now
        handler.postDelayed(syncRunnable, delay)
    }

    private fun runSync() {
        syncScheduled = false
        if (!hasAudioPermission()) return
        if (syncJob?.isActive == true) {
            syncPendingWhileRunning = true
            return
        }
        syncJob = applicationScope.launch(dispatchers.io) {
            runCatching {
                // Original v5.j: full MediaStore path-diff into local DB.
                syncMediaStoreLibraryUseCase(incremental = false)
            }.onFailure { error ->
                Log.e(TAG, "MediaStore sync failed", error)
            }.also {
                if (syncPendingWhileRunning) {
                    syncPendingWhileRunning = false
                    scheduleSync()
                }
            }
        }
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(appContext, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun buildObservedUris(): List<Uri> {
        val uris = linkedSetOf<Uri>()
        uris += MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        uris += MediaStore.Files.getContentUri("external")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            uris += MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            uris += MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            uris += MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            uris += MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }

        return uris.toList()
    }

    private companion object {
        const val TAG = "MediaStoreObserver"
        /** Original v5.m coalesce window. */
        const val SYNC_DEBOUNCE_MS = 6_000L
    }
}
