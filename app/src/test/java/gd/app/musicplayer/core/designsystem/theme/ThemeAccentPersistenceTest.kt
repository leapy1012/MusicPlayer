package gd.app.musicplayer.core.designsystem.theme

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import gd.app.musicplayer.core.datastore.ThemeSettingPreferenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
class ThemeAccentPersistenceTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun selectingThemesResetsAccentButEditingAppearanceDoesNot() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val store = ThemeSettingPreferenceStore(PreferenceDataStoreFactory.create(scope = scope,
                produceFile = { folder.root.resolve("themes.preferences_pb") }))
            assertEquals(ThemeManager.THEME_TYPE_LIGHT, store.getSettingsSnapshot().themeType)
            assertEquals(ThemeAccentDefaults.BLUE, store.getThemeColor())
            store.setThemeColor(0xff123456.toInt())
            store.setThemeType(ThemeManager.THEME_TYPE_PICTURE)
            assertEquals(ThemeAccentDefaults.YELLOW, store.getThemeColor())
            assertFalse(store.getSettingsSnapshot().hasCustomAccent)
            store.setThemeColor(0xff123456.toInt())
            store.setThemeType(ThemeManager.THEME_TYPE_PICTURE, resetAccent = false)
            assertEquals(0xff123456.toInt(), store.getThemeColor())
            store.setThemeType(ThemeManager.THEME_TYPE_LIGHT)
            assertEquals(ThemeAccentDefaults.BLUE, store.getThemeColor())
            store.setThemeColor(0xff123456.toInt())
            store.setThemeType(ThemeManager.THEME_TYPE_DARK)
            assertEquals(ThemeAccentDefaults.BLUE, store.getThemeColor())
            assertFalse(store.getSettingsSnapshot().hasCustomAccent)
        } finally { scope.cancel() }
    }
}
