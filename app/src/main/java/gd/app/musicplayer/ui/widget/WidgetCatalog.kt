package gd.app.musicplayer.ui.widget

import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import gd.app.musicplayer.R
import gd.app.musicplayer.ui.widget.provider.Widget2x1Provider
import gd.app.musicplayer.ui.widget.provider.Widget3x2Provider
import gd.app.musicplayer.ui.widget.provider.Widget4x1Provider
import gd.app.musicplayer.ui.widget.provider.Widget4x2Provider
import gd.app.musicplayer.ui.widget.provider.Widget4x3Provider
import gd.app.musicplayer.ui.widget.provider.Widget4x4Provider
import gd.app.musicplayer.ui.widget.provider.WidgetListProvider

data class WidgetThemeOption(
    val themeType: Int,
    val index: Int,
    @param:DrawableRes val drawableRes: Int,
    val alpha: Float
)

/*
     * Best long-term:
     * Add `useDarkForeground: Boolean` to WidgetThemeOption.
     *
     * For now, this keeps your existing behavior but centralizes it.
     */
fun WidgetThemeOption.shouldUseDarkForeground(): Boolean {
    return drawableRes == R.drawable.widget_color_bg_012
}

data class WidgetStyleOption(
    val styleKey: String,
    @param:LayoutRes val layoutRes: Int,
    @param:DrawableRes val previewRes: Int
)

enum class WidgetArtworkStyle(
    @DrawableRes val previewRes: Int,
    @DrawableRes val placeholderRes: Int
) {
    DEFAULT(
        previewRes = R.drawable.widget_preview_album,
        placeholderRes = R.drawable.widget_default_album
    ),
    ROUNDED(
        previewRes = R.drawable.widget_preview_album_corner,
        placeholderRes = R.drawable.widget_default_album_corner
    ),
    CIRCLE(
        previewRes = R.drawable.widget_preview_album_circle,
        placeholderRes = R.drawable.widget_default_album_circle
    ),
    CIRCLE_LARGE(
        previewRes = R.drawable.widget_preview_album_circle_large,
        placeholderRes = R.drawable.widget_default_album_circle_large
    ),
    CIRCLE_TRANSPARENT(
        previewRes = R.drawable.widget_preview_album_circle,
        placeholderRes = R.drawable.widget_default_album_circle_t
    )
}

data class WidgetProviderSpec(
    val classify: String,
    val titleRes: Int,
    @param:DrawableRes val previewRes: Int,
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

    val themeOptions: List<WidgetThemeOption> = listOf(
        WidgetThemeOption(0, 12, R.drawable.widget_color_bg_013, 0.7f),
        WidgetThemeOption(0, 13, R.drawable.widget_color_bg_014, 0.7f),
        WidgetThemeOption(0, 14, R.drawable.widget_color_bg_015, 0.7f),
        WidgetThemeOption(0, 15, R.drawable.widget_color_bg_016, 0.7f),
        WidgetThemeOption(0, 10, R.drawable.widget_color_bg_011, 0.7f),
        WidgetThemeOption(0, 11, R.drawable.widget_color_bg_012, 0.7f),

        WidgetThemeOption(1, 0, R.drawable.widget_image_bg_001, 0.6f),
        WidgetThemeOption(1, 1, R.drawable.widget_image_bg_002, 0.6f),
        WidgetThemeOption(1, 2, R.drawable.widget_image_bg_003, 0.6f),
        WidgetThemeOption(1, 3, R.drawable.widget_image_bg_004, 0.6f),
        WidgetThemeOption(1, 4, R.drawable.widget_image_bg_005, 0.6f),
        WidgetThemeOption(1, 5, R.drawable.widget_image_bg_006, 0.6f),
        WidgetThemeOption(1, 6, R.drawable.widget_image_bg_007, 0.6f),
        WidgetThemeOption(1, 7, R.drawable.widget_image_bg_008, 0.6f),
        WidgetThemeOption(1, 8, R.drawable.widget_image_bg_009, 0.6f),
        WidgetThemeOption(1, 9, R.drawable.widget_image_bg_010, 0.6f),
        WidgetThemeOption(1, 10, R.drawable.widget_image_bg_011, 0.6f),
        WidgetThemeOption(1, 11, R.drawable.widget_image_bg_012, 0.6f),
        WidgetThemeOption(1, 12, R.drawable.widget_image_bg_013, 0.6f),
        WidgetThemeOption(1, 13, R.drawable.widget_image_bg_014, 0.6f),
        WidgetThemeOption(1, 14, R.drawable.widget_image_bg_015, 0.6f),

        WidgetThemeOption(0, 0, R.drawable.widget_color_bg_001, 0.7f),
        WidgetThemeOption(0, 1, R.drawable.widget_color_bg_002, 0.7f),
        WidgetThemeOption(0, 2, R.drawable.widget_color_bg_003, 0.7f),
        WidgetThemeOption(0, 3, R.drawable.widget_color_bg_004, 0.7f),
        WidgetThemeOption(0, 4, R.drawable.widget_color_bg_005, 0.7f),
        WidgetThemeOption(0, 5, R.drawable.widget_color_bg_006, 0.7f),
        WidgetThemeOption(0, 6, R.drawable.widget_color_bg_007, 0.7f),
        WidgetThemeOption(0, 7, R.drawable.widget_color_bg_008, 0.7f),
        WidgetThemeOption(0, 8, R.drawable.widget_color_bg_009, 0.7f),
        WidgetThemeOption(0, 9, R.drawable.widget_color_bg_010, 0.7f)
    )

    val items: List<WidgetProviderSpec> = listOf(
        WidgetProviderSpec(
            classify = "2*1",
            titleRes = R.string.widget_2x1,
            previewRes = R.drawable.widget_2x1_1,
            providerClass = Widget2x1Provider::class.java,
            styles = listOf(
                WidgetStyleOption(
                    styleKey = "2x1_2",
                    layoutRes = R.layout.widget_2x1_2,
                    previewRes = R.drawable.widget_2x1_2
                ),
                WidgetStyleOption(
                    styleKey = "2x1_1",
                    layoutRes = R.layout.widget_2x1_1,
                    previewRes = R.drawable.widget_2x1_1
                )
            )
        ),
        WidgetProviderSpec(
            classify = "3*2",
            titleRes = R.string.widget_3x2,
            previewRes = R.drawable.widget_3x2_1,
            providerClass = Widget3x2Provider::class.java,
            styles = listOf(
                WidgetStyleOption(
                    styleKey = "3x2_2",
                    layoutRes = R.layout.widget_3x2_2,
                    previewRes = R.drawable.widget_3x2_2
                ),
                WidgetStyleOption(
                    styleKey = "3x2_1",
                    layoutRes = R.layout.widget_3x2_1,
                    previewRes = R.drawable.widget_3x2_1
                )
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
                WidgetStyleOption(
                    styleKey = "LIST",
                    layoutRes = R.layout.widget_queue,
                    previewRes = R.drawable.widget_queue
                )
            )
        )
    )

    fun specForClassify(classify: String): WidgetProviderSpec {
        return items.firstOrNull { item ->
            item.classify == classify
        } ?: defaultSpec()
    }

    fun specForProvider(providerClass: Class<*>): WidgetProviderSpec {
        return items.firstOrNull { item ->
            item.providerClass == providerClass
        } ?: defaultSpec()
    }

    fun classifyForProvider(providerClass: Class<*>): String {
        return specForProvider(providerClass).classify
    }

    fun themeOption(
        themeType: Int,
        themeIndex: Int
    ): WidgetThemeOption {
        return themeOptions.firstOrNull { option ->
            option.themeType == themeType && option.index == themeIndex
        } ?: defaultThemeOption()
    }

    fun defaultStyle(classify: String): WidgetStyleOption {
        return specForClassify(classify).styles.first()
    }

    fun artworkStyle(styleKey: String): WidgetArtworkStyle {
        return when (styleKey.lowercase()) {
            "2x1_2",
            "4x1_3",
            "4x2_3",
            "4x3_5" -> WidgetArtworkStyle.CIRCLE

            "3x2_2",
            "4x2_2",
            "4x3_1" -> WidgetArtworkStyle.ROUNDED

            "4x1_4",
            "4x3_4",
            "4x4_2" -> WidgetArtworkStyle.CIRCLE_TRANSPARENT

            "4x3_6",
            "4x4_3" -> WidgetArtworkStyle.CIRCLE_LARGE

            else -> WidgetArtworkStyle.DEFAULT
        }
    }

    fun defaultThemeOption(): WidgetThemeOption {
        return themeOptions.first { option ->
            option.themeType == DEFAULT_THEME_TYPE &&
                    option.index == DEFAULT_THEME_INDEX
        }
    }

    private fun defaultSpec(): WidgetProviderSpec {
        return items.first { item ->
            item.classify == DEFAULT_CLASSIFY
        }
    }

    private const val DEFAULT_CLASSIFY = "4*1"
    private const val DEFAULT_THEME_TYPE = 0
    private const val DEFAULT_THEME_INDEX = 5
}
