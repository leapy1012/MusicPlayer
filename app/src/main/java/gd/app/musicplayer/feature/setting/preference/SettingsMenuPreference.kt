package gd.app.musicplayer.feature.setting.preference

import android.content.Context
import android.util.AttributeSet
import com.coui.appcompat.preference.COUIMenuPreference

/**
 * App-side [COUIMenuPreference]. Entries must be set before the first bind
 * (SettingsPreferenceFragment does it in onCreatePreferences); otherwise the popup
 * click helper never registers and the menu doesn't open.
 */
class SettingsMenuPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : COUIMenuPreference(context, attrs)
