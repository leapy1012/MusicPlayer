package gd.app.musicplayer.core.designsystem.theme

import android.app.Application
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import com.coui.appcompat.dialog.COUIAlertDialogBuilder
import com.coui.appcompat.R as CouiR
import gd.app.musicplayer.R
import gd.app.musicplayer.core.designsystem.dialog.CouiDialogs
import gd.app.musicplayer.ui.common.base.QueueClearConfirmDialogFragment
import gd.app.musicplayer.feature.library.options.DeleteConfirmDialogFragment
import gd.app.musicplayer.feature.library.tracks.ClearMusicSetConfirmDialogFragment
import gd.app.musicplayer.feature.library.deleted.DeletedMusicDeleteConfirmDialogFragment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=Application::class, manifest=Config.NONE)
class DestructiveConfirmTest {
    @Test fun clearAndDeleteDialogsExplicitlyDeclareDestructiveActions() {
        for (dialog in listOf(QueueClearConfirmDialogFragment(), DeleteConfirmDialogFragment(),
                ClearMusicSetConfirmDialogFragment(), DeletedMusicDeleteConfirmDialogFragment())) {
            val method = dialog.javaClass.getDeclaredMethod("isPositiveDestructive")
            method.isAccessible = true
            assertEquals(true, method.invoke(dialog))
        }
    }

    @Test fun destructiveButtonUsesCouiErrorInDayAndNightWithoutRecoloringCancel() {
        for (theme in listOf(CouiR.style.Theme_COUI_Main_Light, CouiR.style.Theme_COUI_Main_Dark)) {
            val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
            controller.get().setTheme(theme)
            val activity = controller.setup().get()
            val dialog = COUIAlertDialogBuilder(activity, CouiR.style.COUIAlertDialog_Center)
                .setTitle("Clear").setPositiveButton("Clear", null).setNegativeButton("Cancel", null).show()
            val cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE).currentTextColor
            CouiDialogs.applyDestructivePositive(dialog)
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val a = positive.context.obtainStyledAttributes(intArrayOf(CouiR.attr.couiColorError))
            val expected = try { a.getColor(0, 0) } finally { a.recycle() }
            assertEquals(expected, positive.currentTextColor)
            assertTrue(Color.red(expected) > Color.blue(expected))
            assertEquals(cancel, dialog.getButton(AlertDialog.BUTTON_NEGATIVE).currentTextColor)
            assertEquals(CouiR.attr.couiColorError, positive.getTag(R.id.live_theme_text_attribute))
            dialog.dismiss()
            controller.pause().stop().destroy()
        }
    }
}
