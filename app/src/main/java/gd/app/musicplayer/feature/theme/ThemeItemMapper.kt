package gd.app.musicplayer.feature.theme

import gd.app.musicplayer.data.model.ThemeGroup
import gd.app.musicplayer.data.model.ThemeItem
import java.io.File

object ThemeItemMapper {

    fun buildItems(
        themes: List<ThemeGroup>,
        selectedImageName: String,
        customImageNames: List<String>,
        selectedTabIndex: Int,
        tabIndex: Int
    ): List<ThemeItem> {
        return if (tabIndex == 0) {
            buildList {
                themes.forEachIndexed { groupIndex, group ->
                    group.imageNames.forEachIndexed { itemIndex, imageName ->
                        add(
                            ThemeItem(
                                id = stableId(0, groupIndex, itemIndex, imageName),
                                fileName = imageName,
                                isSelected = imageName == selectedImageName
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
                                    isSelected = imagePath == selectedImageName
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
                            isSelected = true
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
                            isSelected = imageName == selectedImageName &&
                                    tabIndex == selectedTabIndex
                        )
                    )
                }
            }
        }
    }

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
