package gd.app.musicplayer.playback

import android.content.Context
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.util.ToastUtil

object SleepTimerFeedback {

    fun showScheduled(context: Context, durationMs: Long) {
        if (durationMs <= 0L) {
            ToastUtil.show(context, R.string.sleep_close)
            return
        }

        val minutes = durationMs / 60_000L
        val fractional = ((((durationMs / 1000L) % 60L) / 60.0f) * 100.0f).toLong()
        val value = buildString {
            append(minutes)
            if (fractional > 0L) {
                append('.')
                append(fractional)
            }
        }

        ToastUtil.show(
            context,
            context.getString(R.string.sleep_mode_tips, value)
        )
    }
}
