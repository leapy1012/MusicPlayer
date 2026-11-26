package gd.app.musicplayer.core.common.extension

import android.app.Activity
import android.app.Application
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import gd.app.musicplayer.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=Application::class, manifest=Config.NONE)
class PlayerNavigationSurfaceTest {
    private fun layout(id: Int, foregroundId: Int, right: Int=0, bottom: Int=48): ViewGroup {
        val c = Robolectric.buildActivity(Activity::class.java)
        c.get().setTheme(R.style.AppTheme)
        val activity = c.setup().get()
        val root = LayoutInflater.from(activity).inflate(id, null) as ViewGroup
        activity.setContentView(root)
        val foreground = root.findViewById<View>(foregroundId)
        if (foregroundId == R.id.music_play_foreground) {
            foreground.applySystemBarInsets(bottomPaddingView=foreground, reserveBottomNavigation=false)
        }
        ViewCompat.dispatchApplyWindowInsets(root, WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0,0,right,bottom))
            .setInsets(WindowInsetsCompat.Type.tappableElement(), Insets.of(0,0,right,bottom)).build())
        val width = if (right > 0) 900 else 400
        val height = if (right > 0) 400 else 900
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0,0,width,height)
        return root
    }
    private fun bounds(root: ViewGroup, id: Int): Rect = Rect().also {
        val view=root.findViewById<View>(id)
        view.getDrawingRect(it)
        root.offsetDescendantRectToMyCoords(view,it)
    }

    @Test fun fullPlayerRetainsOriginalBottomPositionWithFullWindowArtwork() {
        val root=layout(R.layout.activity_musicplay,R.id.music_play_foreground)
        assertEquals(900,bounds(root,R.id.music_play_skin).bottom)
        assertEquals(900,bounds(root,R.id.music_play_content).bottom)
        assertEquals(900,bounds(root,R.id.music_play_lyric_container).bottom)
        assertEquals(0,root.paddingBottom)
    }

    @Test @Config(qualifiers="land") fun landscapeArtworkCoversSideNavigation() {
        val root=layout(R.layout.activity_musicplay,R.id.music_play_foreground,right=48,bottom=0)
        assertEquals(900,bounds(root,R.id.music_play_skin).right)
        assertEquals(852,bounds(root,R.id.music_play_content).right)
        assertEquals(852,bounds(root,R.id.music_play_lyric_container).right)
    }

    @Test fun immersiveLockPlayerRetainsOriginalBottomControlPosition() {
        val root=layout(R.layout.activity_lock,R.id.lock_foreground)
        assertEquals(900,bounds(root,R.id.lock_play_skin).bottom)
        assertTrue(bounds(root,R.id.lock_unlock_hint).bottom > 852)
        assertEquals(0,root.findViewById<View>(R.id.lock_foreground).paddingBottom)
    }
}
