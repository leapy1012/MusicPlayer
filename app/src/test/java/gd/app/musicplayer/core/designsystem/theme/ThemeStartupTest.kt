package gd.app.musicplayer.core.designsystem.theme

import android.app.Application
import android.content.res.Configuration
import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import gd.app.musicplayer.core.datastore.ThemeSettingPreferenceStore
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
class ThemeStartupTest {
    @Test fun directLaunchGetsSavedPictureSettingsBeforeTheAsyncCollectorRuns() {
        val settings = preferencesOf(
            intPreferencesKey("theme_type") to ThemeManager.THEME_TYPE_PICTURE,
            stringPreferencesKey("image_name") to "nature_03.webp",
            intPreferencesKey("theme_blur") to 12,
            intPreferencesKey("theme_overlay_color") to 0x44000000,
            intPreferencesKey("theme_color") to 0xffe0bc20.toInt()
        )
        withManager(settings) { manager ->
            val palette = manager.getCurrentTheme() as PictureThemePalette
            assertEquals(ThemeManager.THEME_TYPE_PICTURE, palette.getThemeType())
            assertEquals("nature_03.webp", palette.getImageName())
            assertEquals(12, palette.getBlurAmount())
            assertEquals(0x44000000, palette.getBackgroundOverlayColor())
            assertEquals(0xffe0bc20.toInt(), palette.getAccentColor())
            assertSame(palette, manager.getCurrentTheme())
        }
    }

    @Test fun savedThemeTypesAndDefaultAccentsAreAvailableOnFirstPalette() {
        for (type in listOf(ThemeManager.THEME_TYPE_LIGHT, ThemeManager.THEME_TYPE_DARK, ThemeManager.THEME_TYPE_PICTURE)) {
            withManager(preferencesOf(intPreferencesKey("theme_type") to type)) { manager ->
                assertEquals(type, manager.getCurrentTheme().getThemeType())
                assertEquals(ThemeAccentDefaults.forTheme(type), manager.getCurrentTheme().getAccentColor())
            }
        }
        withManager(preferencesOf()) { manager ->
            assertEquals(ThemeManager.THEME_TYPE_LIGHT, manager.getCurrentTheme().getThemeType())
        }
    }

    private fun withManager(settings: Preferences, verify: (ThemeManager) -> Unit) {
        var reads = 0
        var readOnMain = true
        val store = object : DataStore<Preferences> {
            override val data = flow {
                reads++
                readOnMain = Looper.myLooper() == Looper.getMainLooper()
                delay(25) // Reproduce disk readiness arriving after an immediate activity launch.
                emit(settings)
            }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                error("Startup must not overwrite the saved preferences")
        }
        val pending = mutableListOf<Runnable>()
        val dispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) { pending += block }
        }
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val app = RuntimeEnvironment.getApplication()
        val context = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        })
        val loader = ThemeBitmapLoader()
        try {
            val manager = ThemeManager(context, ThemeSettingPreferenceStore(store),
                ThemeRegistry(DefaultThemeProvider(loader)), loader, scope)
            verify(manager)
            assertEquals("Only one initial settings snapshot is needed", 1, reads)
            assertFalse("DataStore reading runs on IO", readOnMain)
            assertTrue("The collector has not run yet", pending.isNotEmpty())
        } finally {
            scope.cancel()
            pending.clear()
        }
    }
}
