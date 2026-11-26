package gd.app.musicplayer.core.common

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppForegroundTracker : Application.ActivityLifecycleCallbacks {

    private val mutableIsForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = mutableIsForeground.asStateFlow()

    private var startedActivityCount = 0
    private val activities = java.util.Collections.newSetFromMap(java.util.WeakHashMap<Activity, Boolean>())
    private val mutableActivityCount = MutableStateFlow(0)
    val activeActivityCount: StateFlow<Int> = mutableActivityCount.asStateFlow()

    /** Explicit Quit only. Ordinary Back just finishes its own activity. */
    fun finishAllActivitiesAndRemoveTasks() {
        activities.toList().forEach { activity ->
            if (!activity.isFinishing) activity.finishAndRemoveTask()
        }
    }

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount += 1
        mutableIsForeground.value = startedActivityCount > 0
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
        mutableIsForeground.value = startedActivityCount > 0
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        activities.add(activity)
        mutableActivityCount.value = activities.size
    }

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        activities.remove(activity)
        mutableActivityCount.value = activities.size
    }
}
