package gd.app.musicplayer.domain.model

data class ThemeGroup(
    val type: String,
    val imageNames: List<String>
) {

    val isEmpty: Boolean
        get() = imageNames.isEmpty()
}