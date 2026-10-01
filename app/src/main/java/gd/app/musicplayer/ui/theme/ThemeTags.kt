package gd.app.musicplayer.ui.theme

object ThemeTags {

    object Core {
        const val ACTIVITY_BACKGROUND = "activityBackgroundColor"
        const val BLUR_BACKGROUND = "blurBackground"
        const val CONTENT_BACKGROUND = "contentBackground"
        const val TITLE_BACKGROUND_COLOR = "titleBackgroundColor"
        const val BOTTOM_CONTROL_BACKGROUND = "bottomControlBackground"
        const val BOTTOM_CONTROL_DIVIDER = "bottomControlDivider"
        /** Selection / edit bottom bar hairline (original decoded tag). */
        const val BOTTOM_DIVIDER = "bottomDivider"
    }

    object Text {
        const val ITEM_TEXT_COLOR = "itemTextColor"
        const val ITEM_TEXT_SECONDARY = "itemTextExtraColor"
        /** Edit-bottom icon+label color (original decoded tag; also used on icons). */
        const val BOTTOM_MENU_TEXT = "bottomMenuText"
        /** Edit-bottom icon-only variant from music_edit_menu_item. */
        const val BOTTOM_MENU_ICON = "bottomMenuIcon"
    }

    object Navigation {
        const val TOOLBAR = "toolbar"
        /** Edit-bottom row press/ripple host (original decoded tag). */
        const val BOTTOM_MENU_ITEM = "bottomMenuItem"
    }

    object Progress {
        const val SEEK_BAR = "seekBar"
        /** Skeuomorphic EQ seek (pictured) — ridged thumb + accent track. */
        const val EQUALIZER_SEEK_BAR = "equalizerSeekBar"
        /** Bass Boost / Virtualizer / balance rotary knobs (pictured). */
        const val EQUALIZER_ROTATE_STEP_BAR = "equalizerRotateStepBar"
        /** Pictured reverb chip face. */
        const val REVERB_ITEM = "reverbItem"
    }
}
