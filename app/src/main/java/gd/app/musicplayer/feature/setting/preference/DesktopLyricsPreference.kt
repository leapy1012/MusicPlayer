package gd.app.musicplayer.feature.setting.preference

import android.content.Context
import android.util.AttributeSet
import androidx.preference.PreferenceViewHolder
import com.coui.appcompat.cardlist.COUICardListHelper
import com.coui.appcompat.preference.COUIPreference
import gd.app.musicplayer.R
import gd.app.musicplayer.core.datastore.DesktopLyricPreference
import gd.app.musicplayer.core.designsystem.view.PreferenceDeskLrcItemView

/**
 * Wraps [PreferenceDeskLrcItemView] as a COUI Preference row so desktop lyrics
 * stay on the PreferenceFragment list (card + divider + overscroll).
 */
class DesktopLyricsPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : COUIPreference(context, attrs) {

    var onVisibleChanged: ((Boolean) -> Unit)? = null
    var onLockedChanged: ((Boolean) -> Unit)? = null
    var onPendingEnableAfterPermissionChanged: ((Boolean) -> Unit)? = null

    private var boundView: PreferenceDeskLrcItemView? = null
    private var pendingState: DesktopLyricPreference? = null

    init {
        layoutResource = R.layout.preference_desktop_lyrics_row
        isSelectable = false
        isPersistent = false
    }

    fun render(preference: DesktopLyricPreference) {
        pendingState = preference
        boundView?.render(preference)
    }

    fun resumeDesktopLyricsAfterOverlayPermissionChange() {
        boundView?.resumeDesktopLyricsAfterOverlayPermissionChange()
    }

    fun disableDesktopLyricsIfOverlayPermissionWasRevoked() {
        boundView?.disableDesktopLyricsIfOverlayPermissionWasRevoked()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val view = holder.itemView as? PreferenceDeskLrcItemView ?: return
        boundView = view
        // Preference.onBindViewHolder sets clickable=false when isSelectable=false and
        // replaces OnClickListener — restore desk-lrc row interaction after that.
        view.ensureInteractive()
        view.onVisibleChanged = onVisibleChanged
        view.onLockedChanged = onLockedChanged
        view.onPendingEnableAfterPermissionChanged = onPendingEnableAfterPermissionChanged
        pendingState?.let(view::render)
        COUICardListHelper.setItemCardBackground(
            view,
            COUICardListHelper.getPositionInGroup(this)
        )
    }
}
