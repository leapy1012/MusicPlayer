package gd.app.musicplayer.playback.shutdown

data class ShutdownOptions(
    val clearQueue: Boolean,
    val clearPersistedQueue: Boolean,
    val persistQueueSnapshotWhenKeepingQueue: Boolean,
    val clearRuntimeState: Boolean,
    val removeNotification: Boolean,
    val stopService: Boolean
) {

    companion object {

        val StopAndClearQueue = ShutdownOptions(
            clearQueue = true,
            clearPersistedQueue = false,
            persistQueueSnapshotWhenKeepingQueue = false,
            clearRuntimeState = true,
            removeNotification = true,
            stopService = true
        )

        val StopWithoutClearingQueue = ShutdownOptions(
            clearQueue = false,
            clearPersistedQueue = false,
            persistQueueSnapshotWhenKeepingQueue = true,
            clearRuntimeState = false,
            removeNotification = true,
            stopService = true
        )

        val StopInPlace = ShutdownOptions(
            clearQueue = false,
            clearPersistedQueue = false,
            persistQueueSnapshotWhenKeepingQueue = false,
            clearRuntimeState = false,
            removeNotification = false,
            stopService = false
        )

        val ExitService = ShutdownOptions(
            clearQueue = false,
            clearPersistedQueue = false,
            persistQueueSnapshotWhenKeepingQueue = true,
            clearRuntimeState = false,
            removeNotification = true,
            stopService = true
        )

        val ClearQueueKeepingNotification = ShutdownOptions(
            clearQueue = true,
            clearPersistedQueue = true,
            persistQueueSnapshotWhenKeepingQueue = false,
            clearRuntimeState = true,
            removeNotification = false,
            stopService = false
        )

        val TaskRemovedWhenPaused = ShutdownOptions(
            clearQueue = false,
            clearPersistedQueue = false,
            persistQueueSnapshotWhenKeepingQueue = true,
            clearRuntimeState = false,
            removeNotification = true,
            stopService = true
        )
    }
}
