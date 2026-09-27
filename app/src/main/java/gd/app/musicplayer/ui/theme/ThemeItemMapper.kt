package gd.app.musicplayer.ui.theme

import gd.app.musicplayer.core.designsystem.theme.ThemeManager
import gd.app.musicplayer.domain.model.ThemeGroup
import gd.app.musicplayer.domain.model.ThemeItem
import java.io.File

object ThemeItemMapper {

    /** Sentinel filename for the solid White / COUI light theme tile. */
    const val WHITE_THEME_TOKEN = "__theme_white__"

    fun buildItems(
        themes: List<ThemeGroup>,
        selectedImageName: String,
        customImageNames: List<String>,
        selectedTabIndex: Int,
        tabIndex: Int,
        themeType: Int = ThemeManager.THEME_TYPE_PICTURE
    ): List<ThemeItem> {
        val pictureSelected = themeType == ThemeManager.THEME_TYPE_PICTURE
        return if (tabIndex == 0) {
            buildList {
                add(
                    ThemeItem(
                        id = WHITE_THEME_ID,
                        fileName = WHITE_THEME_TOKEN,
                        isSelected = themeType == ThemeManager.THEME_TYPE_LIGHT
                    )
                )

                themes.forEachIndexed { groupIndex, group ->
                    group.imageNames.forEachIndexed { itemIndex, imageName ->
                        add(
                            ThemeItem(
                                id = stableId(0, groupIndex, itemIndex, imageName),
                                fileName = imageName,
                                isSelected = pictureSelected && imageName == selectedImageName
                            )
                        )
                    }
                }

                customImageNames
                    .filter { File(it).isAbsolute && File(it).exists() }
                    .forEachIndexed { itemIndex, imagePath ->
                        if (none { it.fileName == imagePath }) {
                            add(
                                ThemeItem(
                                    id = stableId(0, themes.size, itemIndex, imagePath),
                                    fileName = imagePath,
                                    isSelected = pictureSelected && imagePath == selectedImageName
                                )
                            )
                        }
                    }

                val selectedCustomImage = selectedImageName.takeIf {
                    File(it).isAbsolute && File(it).exists()
                }
                if (selectedCustomImage != null && none { it.fileName == selectedCustomImage }) {
                    add(
                        ThemeItem(
                            id = stableId(0, themes.size + 1, 0, selectedCustomImage),
                            fileName = selectedCustomImage,
                            isSelected = pictureSelected
                        )
                    )
                }
            }
        } else {
            val imageNames = themes.getOrNull(tabIndex - 1)?.imageNames.orEmpty()

            buildList(imageNames.size) {
                imageNames.forEachIndexed { itemIndex, imageName ->
                    add(
                        ThemeItem(
                            id = stableId(tabIndex, tabIndex - 1, itemIndex, imageName),
                            fileName = imageName,
                            isSelected = pictureSelected &&
                                imageName == selectedImageName &&
                                tabIndex == selectedTabIndex
                        )
                    )
                }
            }
        }
    }

    private const val WHITE_THEME_ID = Long.MIN_VALUE + 1

    private fun stableId(
        tabIndex: Int,
        groupIndex: Int,
        itemIndex: Int,
        imageName: String
    ): Long {
        var result = 17L
        result = 31L * result + tabIndex
        result = 31L * result + groupIndex
        result = 31L * result + itemIndex
        result = 31L * result + imageName.hashCode()
        return result
    }
}
