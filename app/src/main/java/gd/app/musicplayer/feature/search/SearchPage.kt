package gd.app.musicplayer.feature.search

import androidx.annotation.StringRes
import gd.app.musicplayer.R

/**
 * Original `l5.t1`'s pager: a combined view plus one page per result type.
 *
 * `ALL` is `l5.w1` (every section, capped), `SONGS` is `l5.y1` and the remaining pages are
 * `l5.u1` parameterised by set id.
 */
enum class SearchPage(@param:StringRes @field:StringRes val titleRes: Int) {
    ALL(R.string.themes_classify_all),
    SONGS(R.string.songs),
    ALBUMS(R.string.albums),
    ARTISTS(R.string.artists),
    FOLDERS(R.string.folders),
    PLAYLISTS(R.string.playlists);

    /** `i7.c`'s `z10`: only the combined page caps sections and draws their headers. */
    val isCombined: Boolean
        get() = this == ALL

    companion object {
        val pages: List<SearchPage> = values().toList()

        fun at(position: Int): SearchPage = pages[position]

        /** Original `l5.w1.x`: a section header's "See all" jumps to that section's tab. */
        fun forSectionTitle(@StringRes titleRes: Int): SearchPage =
            pages.firstOrNull { !it.isCombined && it.titleRes == titleRes } ?: SONGS
    }
}
