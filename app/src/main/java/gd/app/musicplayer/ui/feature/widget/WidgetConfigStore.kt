package gd.app.musicplayer.ui.feature.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import gd.app.musicplayer.R
import gd.app.musicplayer.ui.feature.widget.provider.Widget2x1Provider
import gd.app.musicplayer.ui.feature.widget.provider.Widget3x2Provider
import gd.app.musicplayer.ui.feature.widget.provider.Widget4x1Provider
import gd.app.musicplayer.ui.feature.widget.provider.Widget4x2Provider
import gd.app.musicplayer.ui.feature.widget.provider.Widget4x3Provider
import gd.app.musicplayer.ui.feature.widget.provider.Widget4x4Provider
import gd.app.musicplayer.ui.feature.widget.provider.WidgetListProvider

data class WidgetThemeOption(
    val themeType: Int,
    val index: Int,
    @DrawableRes val drawableRes: Int,
    val alpha: Float
)

data class WidgetStyleOption(
    val styleKey: String,
    @LayoutRes val layoutRes: Int,
    @DrawableRes val previewRes: Int
)

data class WidgetProviderSpec(
    val classify: String,
    val titleRes: Int,
    @DrawableRes val previewRes: Int,
    val providerClass: Class<*>,
    val styles: List<WidgetStyleOption>
)

data class WidgetConfig(
    val classify: String,
    val styleKey: String,
    val themeType: Int,
    val themeIndex: Int,
    val alpha: Float
)

object WidgetCatalog {
    private val themeOptionsInternal = buildList {
        add(WidgetThemeOption(0, 12, R.drawable.widget_color_bg_013, 0.7f))
        add(WidgetThemeOption(0, 13, R.drawable.widget_color_bg_014, 0.7f))
        add(WidgetThemeOption(0, 14, R.drawable.widget_color_bg_015, 0.7f))
        add(WidgetThemeOption(0, 15, R.drawable.widget_color_bg_016, 0.7f))
        add(WidgetThemeOption(0, 10, R.drawable.widget_color_bg_011, 0.7f))
        add(WidgetThemeOption(0, 11, R.drawable.widget_color_bg_012, 0.7f))
        add(WidgetThemeOption(1, 0, R.drawable.widget_image_bg_001, 0.6f))
        add(WidgetThemeOption(1, 1, R.drawable.widget_image_bg_002, 0.6f))
        add(WidgetThemeOption(1, 2, R.drawable.widget_image_bg_003, 0.6f))
        add(WidgetThemeOption(1, 3, R.drawable.widget_image_bg_004, 0.6f))
        add(WidgetThemeOption(1, 4, R.drawable.widget_image_bg_005, 0.6f))
        add(WidgetThemeOption(1, 5, R.drawable.widget_image_bg_006, 0.6f))
        add(WidgetThemeOption(1, 6, R.drawable.widget_image_bg_007, 0.6f))
        add(WidgetThemeOption(1, 7, R.drawable.widget_image_bg_008, 0.6f))
        add(WidgetThemeOption(1, 8, R.drawable.widget_image_bg_009, 0.6f))
        add(WidgetThemeOption(1, 9, R.drawable.widget_image_bg_010, 0.6f))
        add(WidgetThemeOption(1, 10, R.drawable.widget_image_bg_011, 0.6f))
        add(WidgetThemeOption(1, 11, R.drawable.widget_image_bg_012, 0.6f))
        add(WidgetThemeOption(1, 12, R.drawable.widget_image_bg_013, 0.6f))
        add(WidgetThemeOption(1, 13, R.drawable.widget_image_bg_014, 0.6f))
        add(WidgetThemeOption(1, 14, R.drawable.widget_image_bg_015, 0.6f))
        add(WidgetThemeOption(0, 0, R.drawable.widget_color_bg_001, 0.7f))
        add(WidgetThemeOption(0, 1, R.drawable.widget_color_bg_002, 0.7f))
        add(WidgetThemeOption(0, 2, R.drawable.widget_color_bg_003, 0.7f))
        add(WidgetThemeOption(0, 3, R.drawable.widget_color_bg_004, 0.7f))
        add(WidgetThemeOption(0, 4, R.drawable.widget_color_bg_005, 0.7f))
        add(WidgetThemeOption(0, 5, R.drawable.widget_color_bg_006, 0.7f))
        add(WidgetThemeOption(0, 6, R.drawable.widget_color_bg_007, 0.7f))
        add(WidgetThemeOption(0, 7, R.drawable.widget_color_bg_008, 0.7f))
        add(WidgetThemeOption(0, 8, R.drawable.widget_color_bg_009, 0.7f))
        add(WidgetThemeOption(0, 9, R.drawable.widget_color_bg_010, 0.7f))
    }

    val themeOptions: List<WidgetThemeOption> = themeOptionsInternal

    val items = listOf(
        WidgetProviderSpec(
            classify = "2*1",
            titleRes = R.string.widget_2x1,
            previewRes = R.drawable.widget_2x1_1,
            providerClass = Widget2x1Provider::class.java,
            styles = listOf(
                WidgetStyleOption("2x1_2", R.layout.widget_2x1_2, R.drawable.widget_2x1_2),
                WidgetStyleOption("2x1_1", R.layout.widget_2x1_1, R.drawable.widget_2x1_1)
            )
        ),
        WidgetProviderSpec(
            classify = "3*2",
            titleRes = R.string.widget_3x2,
            previewRes = R.drawable.widget_3x2_1,
            providerClass = Widget3x2Provider::class.java,
            styles = listOf(
                WidgetStyleOption("3x2_2", R.layout.widget_3x2_2, R.drawable.widget_3x2_2),
                WidgetStyleOption("3x2_1", R.layout.widget_3x2_1, R.drawable.widget_3x2_1)
            )
        ),
        WidgetProviderSpec(
            classify = "4*1",
            titleRes = R.string.widget_4x1,
            previewRes = R.drawable.widget_4x1_1,
            providerClass = Widget4x1Provider::class.java,
            styles = listOf(
                WidgetStyleOption("4x1_1", R.layout.widget_4x1_1, R.drawable.widget_4x1_1),
                WidgetStyleOption("4x1_4", R.layout.widget_4x1_4, R.drawable.widget_4x1_4),
                WidgetStyleOption("4x1_3", R.layout.widget_4x1_3, R.drawable.widget_4x1_3),
                WidgetStyleOption("4x1_2", R.layout.widget_4x1_2, R.drawable.widget_4x1_2)
            )
        ),
        WidgetProviderSpec(
            classify = "4*2",
            titleRes = R.string.widget_4x2,
            previewRes = R.drawable.widget_4x2_2,
            providerClass = Widget4x2Provider::class.java,
            styles = listOf(
                WidgetStyleOption("4x2_2", R.layout.widget_4x2_2, R.drawable.widget_4x2_2),
                WidgetStyleOption("4x2_1", R.layout.widget_4x2_1, R.drawable.widget_4x2_1),
                WidgetStyleOption("4x2_4", R.layout.widget_4x2_4, R.drawable.widget_4x2_4),
                WidgetStyleOption("4x2_3", R.layout.widget_4x2_3, R.drawable.widget_4x2_3)
            )
        ),
        WidgetProviderSpec(
            classify = "4*3",
            titleRes = R.string.widget_4x3,
            previewRes = R.drawable.widget_4x3_1,
            providerClass = Widget4x3Provider::class.java,
            styles = listOf(
                WidgetStyleOption("4x3_3", R.layout.widget_4x3_3, R.drawable.widget_4x3_3),
                WidgetStyleOption("4x3_4", R.layout.widget_4x3_4, R.drawable.widget_4x3_4),
                WidgetStyleOption("4x3_5", R.layout.widget_4x3_5, R.drawable.widget_4x3_5),
                WidgetStyleOption("4x3_6", R.layout.widget_4x3_6, R.drawable.widget_4x3_6),
                WidgetStyleOption("4x3_1", R.layout.widget_4x3_1, R.drawable.widget_4x3_1),
                WidgetStyleOption("4x3_2", R.layout.widget_4x3_2, R.drawable.widget_4x3_2)
            )
        ),
        WidgetProviderSpec(
            classify = "4*4",
            titleRes = R.string.widget_4x4,
            previewRes = R.drawable.widget_4x4_1,
            providerClass = Widget4x4Provider::class.java,
            styles = listOf(
                WidgetStyleOption("4x4_2", R.layout.widget_4x4_2, R.drawable.widget_4x4_2),
                WidgetStyleOption("4x4_3", R.layout.widget_4x4_3, R.drawable.widget_4x4_3),
                WidgetStyleOption("4x4_1", R.layout.widget_4x4_1, R.drawable.widget_4x4_1)
            )
        ),
        WidgetProviderSpec(
            classify = "List",
            titleRes = R.string.widget_list,
            previewRes = R.drawable.widget_queue,
            providerClass = WidgetListProvider::class.java,
            styles = listOf(
                WidgetStyleOption("list", R.layout.widget_queue, R.drawable.widget_queue)
            )
        )
    )

    fun specForClassify(classify: String): WidgetProviderSpec =
        items.firstOrNull { it.classify == classify } ?: items.first { it.classify == "4*1" }

    fun specForProvider(providerClass: Class<*>): WidgetProviderSpec =
        items.firstOrNull { it.providerClass == providerClass } ?: items.first { it.classify == "4*1" }

    fun classifyForProvider(providerClass: Class<*>): String = specForProvider(providerClass).classify

    fun themeOption(themeType: Int, themeIndex: Int): WidgetThemeOption =
        themeOptions.firstOrNull { it.themeType == themeType && it.index == themeIndex }
            ?: themeOptions.first { it.themeType == 0 && it.index == 5 }

    fun defaultStyle(classify: String): WidgetStyleOption = specForClassify(classify).styles.first()
}

class WidgetConfigStore(context: Context) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(appWidgetId: Int, classify: String): WidgetConfig {
        val defaultStyle = WidgetCatalog.defaultStyle(classify)
        val defaultTheme = WidgetCatalog.themeOptions.first { it.themeType == 0 && it.index == 5 }
        val styleKey = preferences.getString(key(KEY_STYLE, appWidgetId), defaultStyle.styleKey)
            ?: defaultStyle.styleKey
        val themeType = preferences.getInt(key(KEY_THEME_TYPE, appWidgetId), defaultTheme.themeType)
        val themeIndex = preferences.getInt(key(KEY_THEME_INDEX, appWidgetId), defaultTheme.index)
        val alpha = preferences.getFloat(key(KEY_ALPHA, appWidgetId), defaultTheme.alpha)
        return WidgetConfig(classify, styleKey, themeType, themeIndex, alpha)
    }

    fun save(appWidgetId: Int, config: WidgetConfig) {
        preferences.edit()
            .putString(key(KEY_CLASSIFY, appWidgetId), config.classify)
            .putString(key(KEY_STYLE, appWidgetId), config.styleKey)
            .putInt(key(KEY_THEME_TYPE, appWidgetId), config.themeType)
            .putInt(key(KEY_THEME_INDEX, appWidgetId), config.themeIndex)
            .putFloat(key(KEY_ALPHA, appWidgetId), config.alpha)
            .apply()
    }

    fun delete(appWidgetId: Int) {
        preferences.edit()
            .remove(key(KEY_CLASSIFY, appWidgetId))
            .remove(key(KEY_STYLE, appWidgetId))
            .remove(key(KEY_THEME_TYPE, appWidgetId))
            .remove(key(KEY_THEME_INDEX, appWidgetId))
            .remove(key(KEY_ALPHA, appWidgetId))
            .apply()
    }

    fun loadClassify(appWidgetId: Int): String? =
        preferences.getString(key(KEY_CLASSIFY, appWidgetId), null)

    private fun key(prefix: String, appWidgetId: Int): String = "${prefix}_$appWidgetId"

    private companion object {
        const val PREFS_NAME = "widget_config_store"
        const val KEY_CLASSIFY = "widget_classify"
        const val KEY_STYLE = "widget_style"
        const val KEY_THEME_TYPE = "widget_theme_type"
        const val KEY_THEME_INDEX = "widget_theme_index"
        const val KEY_ALPHA = "widget_alpha"
    }
}
