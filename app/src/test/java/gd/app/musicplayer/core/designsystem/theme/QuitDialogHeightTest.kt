package gd.app.musicplayer.core.designsystem.theme

import android.app.Application
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.coui.appcompat.dialog.COUIAlertDialogBuilder
import com.coui.appcompat.R as CouiR
import gd.app.musicplayer.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
class QuitDialogHeightTest {
    @Test fun titleOnlyConfirmationReceivesNativeCouiMinimumHeight() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.AppTheme)
        val activity = controller.setup().get()
        val builder = COUIAlertDialogBuilder(activity, CouiR.style.COUIAlertDialog_Center)
            .setTitle("Quit the app completely?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Quit", null)
        val dialog = builder.show()
        val panel = dialog.findViewById<View>(CouiR.id.parentPanel)!!
        val density = activity.resources.displayMetrics.density
        panel.measure(View.MeasureSpec.makeMeasureSpec((360 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((800 * density).toInt(), View.MeasureSpec.AT_MOST))
        val minimum = activity.resources.getDimensionPixelSize(CouiR.dimen.coui_alert_dialog_builder_parent_panel_min_height_normal)
        assertTrue("Native title-only confirmation must retain the COUI minimum", panel.measuredHeight >= minimum)
        dialog.dismiss()
        controller.pause().stop().destroy()
    }
}
