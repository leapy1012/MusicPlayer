package gd.app.musicplayer.feature.lock

import android.app.Activity
import android.app.Application
import android.view.View
import androidx.core.view.WindowInsetsControllerCompat
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class,manifest=Config.NONE)
class LockPlayerWindowTest {
    @Test fun lockWindowActuallyHidesNavigationInsteadOfOnlyLayingOutBehindIt() {
        val controller=Robolectric.buildActivity(Activity::class.java).setup()
        val window=controller.get().window
        repeat(2) {
            window.decorView.systemUiVisibility=0
            LockPlayerWindow.apply(window)
            @Suppress("DEPRECATION")
            val flags=window.decorView.systemUiVisibility
            assertTrue(flags and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION != 0)
            assertTrue(flags and View.SYSTEM_UI_FLAG_FULLSCREEN != 0)
            assertTrue(flags and View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY != 0)
            assertFalse(window.isNavigationBarContrastEnforced)
            assertEquals(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
                WindowInsetsControllerCompat(window,window.decorView).systemBarsBehavior)
        }
        controller.pause().stop().destroy()
    }
}
