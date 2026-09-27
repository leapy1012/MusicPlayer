package gd.app.musicplayer.feature.setting.preference

import android.content.Context
import android.util.AttributeSet
import com.coui.appcompat.preference.COUIMenuPreference

/**
 * App-side [COUIMenuPreference] that can force a rebind after [setEntries].
 * Needed because entries are applied in code after the first Preference bind —
 * without [notifyChanged], the click helper stays unregistered and the popup never opens.
 */
class SettingsMenuPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : COUIMenuPreference(context, attrs) {

    fun refreshBoundMenu() {
        notifyChanged()
    }
}
