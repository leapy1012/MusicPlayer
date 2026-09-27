package gd.app.musicplayer.domain.repository

import android.content.Context
import android.util.Xml
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.core.designsystem.theme.ThemeManager
import gd.app.musicplayer.core.designsystem.theme.ThemePalette
import gd.app.musicplayer.core.designsystem.theme.ThemeRegistry
import gd.app.musicplayer.core.designsystem.theme.accentColor
import gd.app.musicplayer.core.designsystem.theme.rippleColor
import gd.app.musicplayer.core.datastore.ThemeSettingPreferenceStore
import gd.app.musicplayer.domain.model.ThemeGroup
import javax.inject.Inject
import javax.inject.Singleton
import org.xmlpull.v1.XmlPullParser

@Singleton
class ThemeRepo @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val themeRegistry: ThemeRegistry,
    private val themeManager: ThemeManager,
    private val themeSettingPreferences: ThemeSettingPreferenceStore
) {

    private var cachedThemeGroups: List<ThemeGroup>? = null

    fun getThemeGroups(): List<ThemeGroup> {
        cachedThemeGroups?.let { return it }

        return parseThemeGroups().also { groups ->
            cachedThemeGroups = groups
        }
    }

    fun refreshTheme(): ThemePalette {
        // Original ensure path [m4.c.c] — no notify when already loaded.
        themeRegistry.refreshTheme(appContext)
        return themeRegistry.getCurrentTheme()
    }

    fun getCorePalette(): ThemePalette {
        return themeRegistry.getCurrentTheme()
    }

    suspend fun applyPictureTheme(
        imageName: String,
        overlayColor: Int? = null,
        blur: Int? = null
    ): ThemePalette {
        val currentSettings = themeSettingPreferences.getSettingsSnapshot()

        savePictureTheme(
            imageName = imageName,
            overlayColor = overlayColor ?: currentSettings.overlayColor,
            blur = blur ?: currentSettings.blur
        )

        // Original theme pick: H() then m()/j(notify) — not a silent ensure.
        return themeManager.applySettingsAndNotify()
    }

    suspend fun updateThemeAppearance(
        imageName: String,
        overlayColor: Int,
        blur: Int
    ): ThemePalette {
        savePictureTheme(
            imageName = imageName,
            overlayColor = overlayColor,
            blur = blur
        )

        return themeManager.applySettingsAndNotify()
    }

    fun updateAccentColor(accentColor: Int): ThemePalette {
        // Original o7.f.v — mutate + notify once (no second refreshTheme).
        themeManager.updateAccentColor(accentColor)
        return themeRegistry.getCurrentTheme()
    }

    fun toggleDarkMode(enabled: Boolean): ThemePalette {
        // Original o7.f.u — clone + bg H() + notify (no second refreshTheme).
        themeManager.toggleDarkMode(enabled)
        return themeRegistry.getCurrentTheme()
    }

    /** User-selected night switch — not system-followed dark appearance. */
    fun isUserDarkModePreferred(): Boolean {
        return themeManager.isUserDarkModePreferred()
    }

    fun applyLightTheme(): ThemePalette {
        themeManager.applyLightTheme()
        return themeRegistry.getCurrentTheme()
    }

    fun isUserLightModePreferred(): Boolean {
        return themeManager.isUserLightModePreferred()
    }

    fun getAccentColor(): Int {
        return getCorePalette().accentColor
    }

    fun getRippleColor(): Int {
        return getCorePalette().rippleColor
    }

    fun clearThemeGroupCache() {
        cachedThemeGroups = null
    }

    private suspend fun savePictureTheme(
        imageName: String,
        overlayColor: Int,
        blur: Int
    ) {
        themeSettingPreferences.setThemeType(ThemeManager.THEME_TYPE_PICTURE)
        themeSettingPreferences.setThemeImageName(imageName)
        themeSettingPreferences.setThemeOverlayColor(overlayColor)
        themeSettingPreferences.setThemeBlur(blur)
    }

    private fun parseThemeGroups(): List<ThemeGroup> {
        val groups = mutableListOf<ThemeGroup>()
        val parser = Xml.newPullParser()

        appContext.assets.open(THEMES_LIST_FILE).use { inputStream ->
            parser.setInput(inputStream, XML_ENCODING)

            var eventType = parser.eventType
            var currentGroupName: String? = null
            var currentImages = mutableListOf<String>()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            TAG_GROUP -> {
                                currentGroupName = parser.getAttributeValue(null, ATTR_NAME)
                                currentImages = mutableListOf()
                            }

                            TAG_PICTURE -> {
                                val imageName = parser.getAttributeValue(null, ATTR_NAME)

                                if (!imageName.isNullOrBlank()) {
                                    currentImages.add(imageName)
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        if (parser.name == TAG_GROUP) {
                            groups.add(
                                ThemeGroup(
                                    currentGroupName.orEmpty(),
                                    currentImages.toList()
                                )
                            )
                        }
                    }
                }

                eventType = parser.next()
            }
        }

        return groups
    }

    private companion object {
        const val THEMES_LIST_FILE = "themes_list.xml"
        const val XML_ENCODING = "UTF-8"

        const val TAG_GROUP = "group"
        const val TAG_PICTURE = "picture"
        const val ATTR_NAME = "name"
    }
}