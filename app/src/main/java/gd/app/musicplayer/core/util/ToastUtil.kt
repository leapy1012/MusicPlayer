package gd.app.musicplayer.core.util

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.widget.Toast
import java.lang.reflect.Field

object ToastUtil {

    private var sharedToast: Toast? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun show(context: Context, messageResId: Int) {
        show(context, Toast.LENGTH_SHORT, context.getString(messageResId))
    }

    fun show(context: Context, message: CharSequence) {
        show(context, Toast.LENGTH_SHORT, message)
    }

    fun show(context: Context, duration: Int, message: CharSequence) {
        if (Thread.currentThread() == Looper.getMainLooper().thread) {
            showInternal(context, duration, message)
        } else {
            mainHandler.post {
                showInternal(context, duration, message)
            }
        }
    }

    fun showLongWithRepeat(context: Context, messageResId: Int, repeatDelayMs: Int) {
        val toast = Toast.makeText(context, context.getString(messageResId), Toast.LENGTH_LONG)
        protectToastHandler(toast)
        toast.show()

        mainHandler.postDelayed({
            toast.show()
        }, repeatDelayMs.toLong())
    }

    private fun showInternal(context: Context, duration: Int, message: CharSequence) {
        sharedToast?.cancel()

        sharedToast = Toast.makeText(context.applicationContext, "", Toast.LENGTH_SHORT).apply {
            protectToastHandler(this)
            setDuration(duration)
            setText(message)
            show()
        }
    }

    private fun protectToastHandler(toast: Toast) {
        if (Build.VERSION.SDK_INT < 25) return

        try {
            val tnField: Field = Toast::class.java.getDeclaredField("mTN").apply {
                isAccessible = true
            }
            val tnInstance = tnField.get(toast)

            val handlerField: Field = tnField.type.getDeclaredField("mHandler").apply {
                isAccessible = true
            }
            val originalHandler = handlerField.get(tnInstance) as Handler
            handlerField.set(tnInstance, SafeToastHandler(originalHandler))
        } catch (_: Exception) {
        }
    }

    private class SafeToastHandler(
        private val originalHandler: Handler
    ) : Handler(Looper.getMainLooper()) {

        override fun dispatchMessage(msg: Message) {
            try {
                super.dispatchMessage(msg)
            } catch (_: Exception) {
            }
        }

        override fun handleMessage(msg: Message) {
            originalHandler.handleMessage(msg)
        }
    }
}
