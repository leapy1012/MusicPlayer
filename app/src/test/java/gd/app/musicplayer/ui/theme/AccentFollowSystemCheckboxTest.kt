package gd.app.musicplayer.ui.theme

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.coui.appcompat.checkbox.COUICheckBox
import gd.app.musicplayer.R
import gd.app.musicplayer.databinding.DialogAccentColorPickerBinding
import gd.app.musicplayer.ui.common.base.NightThemeTestApp
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=NightThemeTestApp::class,manifest=Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class AccentFollowSystemCheckboxTest {
    @Test fun nativeCheckboxCanBeUncheckedAndRestoresCustomDraftInBothThemes() {
        val app=RuntimeEnvironment.getApplication() as NightThemeTestApp
        try {
            for(theme in listOf(R.style.AppTheme,com.coui.appcompat.R.style.Theme_COUI_Main_Dark)) {
                val controller=Robolectric.buildActivity(AppCompatActivity::class.java)
                controller.get().setTheme(theme)
                val activity=controller.setup().get()
                val binding=DialogAccentColorPickerBinding.inflate(activity.layoutInflater)
                activity.setContentView(binding.root)
                val dialog=SelectAccentColorDialog().apply { themeRepo=app.repo }
                fun setField(name:String,value:Any) {
                    SelectAccentColorDialog::class.java.getDeclaredField(name).apply { isAccessible=true }.set(dialog,value)
                }
                setField("binding",binding)
                val custom=0xffca3c80.toInt()
                setField("presetAdapter",PresetColorAdapter(activity.layoutInflater,intArrayOf(custom),dialog))
                SelectAccentColorDialog::class.java.getDeclaredMethod("setupClickListeners",DialogAccentColorPickerBinding::class.java)
                    .apply { isAccessible=true }.invoke(dialog,binding)
                fun snapshot()=Bundle().also(dialog::onSaveInstanceState)
                assertTrue(binding.accentColorDefaultSelect is COUICheckBox)
                dialog.onPresetColorSelected(custom)
                assertFalse(binding.accentColorDefaultSelect.isChecked)
                binding.accentColorDefaultSelect.performClick()
                assertTrue(binding.accentColorDefaultSelect.isChecked)
                assertTrue(snapshot().getBoolean("state_system"))
                binding.accentColorDefaultSelect.performClick()
                assertFalse(binding.accentColorDefaultSelect.isChecked)
                assertFalse(snapshot().getBoolean("state_system"))
                assertEquals(custom,snapshot().getInt("state_selected"))
                binding.accentColorDefaultSelect.performClick()
                dialog.onPresetColorSelected(custom)
                assertFalse(binding.accentColorDefaultSelect.isChecked)
                assertEquals(custom,snapshot().getInt("state_custom_color"))
                controller.pause().stop().destroy()
            }
        } finally { app.scope.cancel() }
    }
}
