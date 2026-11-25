package gd.app.musicplayer.core.common.extension

import android.app.Activity
import android.app.Application
import android.view.View
import android.widget.LinearLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
class NavigationInsetsTest {
    private fun root(): LinearLayout {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(5, 7, 9, 12)
            activity.setContentView(this)
        }
    }

    private fun insets(bottom: Int = 0, left: Int = 0, right: Int = 0, buttons: Boolean = true) =
        WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 24, 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(left, 0, right, bottom))
            .setInsets(WindowInsetsCompat.Type.tappableElement(),
                if (buttons) Insets.of(left, 0, right, bottom) else Insets.NONE)
            .build()

    private fun layout(root: View) {
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 400, 800)
    }

    @Test fun buttonNavigationKeepsListAndConfirmationAboveSystemButtons() {
        val root = root()
        val list = View(root.context)
        val confirm = View(root.context)
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(confirm, LinearLayout.LayoutParams(-1, 44).apply { bottomMargin = 16 })
        root.applySystemBarInsets(bottomPaddingView = root)
        ViewCompat.dispatchApplyWindowInsets(root, insets(bottom = 48))
        layout(root)
        assertEquals(60, root.paddingBottom)
        assertEquals(800 - 60 - 16, confirm.bottom)
        assertEquals(confirm.top, list.bottom)
        confirm.visibility = View.GONE
        layout(root)
        assertEquals(800 - 60, list.bottom)
    }

    @Test fun gestureNavigationAllowsContentBehindIndicator() {
        val root = root()
        val content = View(root.context)
        root.addView(content, LinearLayout.LayoutParams(-1, -1))
        root.applySystemBarInsets(bottomPaddingView = root)
        ViewCompat.dispatchApplyWindowInsets(root, insets(bottom = 24, buttons = false))
        layout(root)
        assertEquals(12, root.paddingBottom)
        assertEquals(788, content.bottom)
    }

    @Test fun repeatedInsetsAndNavigationModeChangesDoNotAccumulatePadding() {
        val root = root()
        root.applySystemBarInsets(bottomPaddingView = root)
        repeat(3) { ViewCompat.dispatchApplyWindowInsets(root, insets(bottom = 48)) }
        assertEquals(60, root.paddingBottom)
        ViewCompat.dispatchApplyWindowInsets(root, insets(bottom = 24, buttons = false))
        assertEquals(12, root.paddingBottom)
        ViewCompat.dispatchApplyWindowInsets(root, insets(bottom = 48))
        assertEquals(60, root.paddingBottom)
    }

    @Test fun landscapeButtonBarReservesItsSideAndClearsPreviousEdge() {
        val root = root()
        root.applySystemBarInsets(bottomPaddingView = root)
        ViewCompat.dispatchApplyWindowInsets(root, insets(right = 48))
        assertEquals(57, root.paddingRight)
        assertEquals(12, root.paddingBottom)
        ViewCompat.dispatchApplyWindowInsets(root, insets(left = 48))
        assertEquals(53, root.paddingLeft)
        assertEquals(9, root.paddingRight)
        ViewCompat.dispatchApplyWindowInsets(root, insets(bottom = 24, buttons = false))
        assertEquals(5, root.paddingLeft)
    }

    @Test fun separatePaddingTargetRetainsItsOwnSpacing() {
        val root = root()
        val target = View(root.context).apply { setPadding(2, 4, 6, 8) }
        root.addView(target)
        root.applySystemBarInsets(bottomPaddingView = target)
        ViewCompat.dispatchApplyWindowInsets(root, insets(bottom = 48))
        assertEquals(56, target.paddingBottom)
        assertEquals(4, target.paddingTop)
        assertEquals(12, root.paddingBottom)
        ViewCompat.dispatchApplyWindowInsets(root, insets(right = 48))
        assertEquals(57, root.paddingRight)
        assertEquals(6, target.paddingRight)
        assertEquals(8, target.paddingBottom)
    }
}
