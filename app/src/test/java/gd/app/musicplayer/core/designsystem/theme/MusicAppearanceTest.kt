package gd.app.musicplayer.core.designsystem.theme

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import gd.app.musicplayer.core.datastore.ThemeSettings
import gd.app.musicplayer.core.datastore.ThemeSettingPreferenceStore
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class,manifest=Config.NONE)
class MusicAppearanceTest {
    @get:Rule val folder=TemporaryFolder()

    @Test fun themeCardsControlLightModeAndSurviveTheTemporarySystemDarkOverride()=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        try {
            val data=PreferenceDataStoreFactory.create(scope=scope) { folder.root.resolve("cards.preferences_pb") }
            val store=ThemeSettingPreferenceStore(data)
            assertTrue(store.getSettingsSnapshot().followSystemAccent)
            store.setThemeColor(0xffc0348a.toInt())
            for(type in listOf(ThemeManager.THEME_TYPE_LIGHT,ThemeManager.THEME_TYPE_DARK,ThemeManager.THEME_TYPE_PICTURE)) {
                store.setThemeType(type,resetAccent=false)
                val settings=store.getSettingsSnapshot()
                assertEquals(type,MusicAppearance.themeType(settings,false))
                assertEquals(ThemeManager.THEME_TYPE_DARK,MusicAppearance.themeType(settings,true))
                assertEquals(type,ThemeSettingPreferenceStore(data).getSettingsSnapshot().themeType)
                assertEquals(0xffc0348a.toInt(),settings.themeColor)
                val cards=gd.app.musicplayer.ui.theme.ThemeItemMapper.buildItems(
                    themes=emptyList(),selectedImageName=settings.imageName,customImageNames=emptyList(),
                    selectedTabIndex=0,tabIndex=0,themeType=MusicAppearance.themeType(settings,true))
                assertEquals(gd.app.musicplayer.ui.theme.ThemeItemMapper.BLACK_THEME_TOKEN,cards.single { it.isSelected }.fileName)
            }
            store.setFollowSystemAccent(true)
            assertEquals(0xff00aaaa.toInt(),MusicAppearance.accent(store.getSettingsSnapshot(),0xff00aaaa.toInt()))
        } finally { scope.cancel() }
    }

    @Test fun retiringTheSettingsOverridePreservesDarkUntilAnotherThemeCardIsSelected()=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        try {
            val data=PreferenceDataStoreFactory.create(scope=scope) { folder.root.resolve("legacy.preferences_pb") }
            data.edit {
                it[intPreferencesKey("theme_type")]=2
                it[intPreferencesKey("appearance_mode")]=2
                it[intPreferencesKey("theme_color")]=0xffdd22aa.toInt()
            }
            val store=ThemeSettingPreferenceStore(data)
            assertEquals(ThemeManager.THEME_TYPE_DARK,store.getSettingsSnapshot().themeType)
            store.setThemeType(ThemeManager.THEME_TYPE_PICTURE,resetAccent=false)
            val picture=store.getSettingsSnapshot()
            assertEquals(ThemeManager.THEME_TYPE_PICTURE,MusicAppearance.themeType(picture,false))
            assertEquals(ThemeManager.THEME_TYPE_DARK,MusicAppearance.themeType(picture,true))
            assertEquals(0xffdd22aa.toInt(),picture.themeColor)
            store.setThemeType(ThemeManager.THEME_TYPE_LIGHT,resetAccent=false)
            assertEquals(ThemeManager.THEME_TYPE_LIGHT,MusicAppearance.themeType(store.getSettingsSnapshot(),false))
        } finally { scope.cancel() }
    }
}
