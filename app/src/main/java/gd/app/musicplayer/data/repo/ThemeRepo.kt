package gd.app.musicplayer.data.repo

import android.content.Context
import android.util.Xml
import gd.app.musicplayer.core.theme.*
import gd.app.musicplayer.data.model.ThemeGroup
import gd.app.musicplayer.core.theme.ThemeManager
import gd.app.musicplayer.core.theme.ThemeRegistry
import gd.app.musicplayer.util.PreferenceUtil
import org.xmlpull.v1.XmlPullParser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeRepo @Inject constructor(
    private val themeRegistry: ThemeRegistry,
    private val themeManager: ThemeManager,
    private val preferenceUtil: PreferenceUtil
) {
    fun parse(context: Context): List<ThemeGroup> {

        val groups = mutableListOf<ThemeGroup>()

        val parser = Xml.newPullParser()
        context.assets.open("themes_list.xml").use { inputStream ->
            parser.setInput(inputStream, "UTF-8")

            var eventType = parser.eventType
            var currentGroupName: String? = null
            val images = mutableListOf<String>()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "group" -> {
                                currentGroupName = parser.getAttributeValue(null, "name")
                                images.clear()
                            }
                            "picture" -> {
                                val imageName = parser.getAttributeValue(null, "name")
                                images.add(imageName)
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        if (parser.name == "group") {
                            groups.add(
                                ThemeGroup(
                                    currentGroupName ?: "",
                                    images.toList()
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

    fun refreshTheme(context: Context): ThemePalette {
        themeRegistry.refreshTheme(context.applicationContext)
        return themeRegistry.getCurrentTheme(context.applicationContext)
    }

    fun getCorePalette(context: Context): ThemePalette =
        themeRegistry.getCurrentTheme(context.applicationContext)

    fun applyPictureTheme(
        context: Context,
        imageName: String,
        overlayColor: Int = preferenceUtil.getThemeOverlayColor(),
        blur: Int = preferenceUtil.getThemeBlur()
    ) {
        preferenceUtil.putIntPreference("theme_type", ThemeManager.THEME_TYPE_PICTURE)
        preferenceUtil.setThemeImageName(imageName)
        preferenceUtil.setThemeOverlayColor(overlayColor)
        preferenceUtil.setThemeBlur(blur)
        refreshTheme(context)
    }

    fun updateThemeAppearance(
        context: Context,
        imageName: String,
        overlayColor: Int,
        blur: Int
    ) {
        preferenceUtil.putIntPreference("theme_type", ThemeManager.THEME_TYPE_PICTURE)
        preferenceUtil.setThemeImageName(imageName)
        preferenceUtil.setThemeOverlayColor(overlayColor)
        preferenceUtil.setThemeBlur(blur)
        refreshTheme(context)
    }

    fun updateAccentColor(context: Context, accentColor: Int) {
        themeManager.updateAccentColor(accentColor)
    }

    fun toggleDarkMode(context: Context, enabled: Boolean) {
        themeManager.toggleDarkMode(enabled)
    }

    fun getAccentColor(context: Context) : Int {
        return getCorePalette(context).accentColor
    }

    fun getRippleColor(context: Context): Int {
        return getCorePalette(context).rippleColor
    }
}
