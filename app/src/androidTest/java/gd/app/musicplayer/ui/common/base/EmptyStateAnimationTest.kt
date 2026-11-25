package gd.app.musicplayer.ui.common.base

import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewStub
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieCompositionFactory
import gd.app.musicplayer.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmptyStateAnimationTest {
    @Test fun rabbitPlaysOnceAndRepeatedEmptyResultsDoNotRestartIt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ContextThemeWrapper(instrumentation.targetContext, R.style.AppTheme)
        val composition = checkNotNull(LottieCompositionFactory.fromAssetSync(context, "music_empty.json").value)
        instrumentation.runOnMainSync {
            val root = FrameLayout(context)
            val list = RecyclerView(context)
            val stub = ViewStub(context).apply { layoutResource = R.layout.layout_list_empty }
            root.addView(list)
            root.addView(stub)
            val controller = RecyclerEmptyStateController(list, stub)
            controller.setEmptyLottieAsset("music_empty.json")
            controller.showEmpty()
            // The COUI spring host owns this container through its adapter, so it is not
            // attached to the test root until a layout pass creates the static-content row.
            val container = RecyclerEmptyStateController::class.java.getDeclaredField("emptyContainer")
                .apply { isAccessible = true }.get(controller) as View
            val animation = container.findViewById<LottieAnimationView>(R.id.empty_lottie)
            animation.setComposition(composition)
            assertEquals(View.GONE, container.findViewById<View>(R.id.empty_image).visibility)
            assertEquals(View.VISIBLE, animation.visibility)
            assertEquals(0, animation.repeatCount)

            // Simulate completion. Repeated zero-result queries must retain the final frame.
            controller.finishAnimation()
            assertFalse(animation.isAnimating)
            assertEquals(1f, animation.progress, 0.001f)
            val finished = animation.progress
            controller.showEmpty()
            assertEquals(finished, animation.progress, 0.001f)
            assertEquals(0, animation.repeatCount)

            // A fresh transition from results to empty starts a new single playback.
            controller.showList()
            controller.showEmpty()
            assertEquals(0f, animation.progress, 0.001f)
            controller.showList()
            animation.cancelAnimation()
        }
    }
}
