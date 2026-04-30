package gd.app.musicplayer.playback.queue

data class QueueUpdateResult(
    val currentItemChanged: Boolean,
    val dataChanged: Boolean,
    val queueChanged: Boolean,
) {
    companion object {
        val NoChange = QueueUpdateResult(
            currentItemChanged = false,
            dataChanged = false,
            queueChanged = false,
        )

        fun success(
            currentItemChanged: Boolean,
            queueChanged: Boolean,
        ): QueueUpdateResult {
            return QueueUpdateResult(
                currentItemChanged = currentItemChanged,
                dataChanged = currentItemChanged,
                queueChanged = queueChanged,
            )
        }
    }
}
