package gd.app.musicplayer.util

interface AppPreferenceOps : PreferenceAccess {

    fun isBluetoothAutoStartEnabled(): Boolean =
        getBooleanPreference(KEY_BLUETOOTH_AUTO_START, false)

    fun isBluetoothAutoStopEnabled(): Boolean =
        getBooleanPreference(KEY_BLUETOOTH_AUTO_STOP, true)

    fun hasShortcutPermission(): Boolean =
        getBooleanPreference(KEY_SHORTCUT_PERMISSION, true)

    fun setShortcutPermissionGranted(granted: Boolean) {
        putBooleanPreference(KEY_SHORTCUT_PERMISSION, granted)
    }

    fun isFirstStart(): Boolean =
        getBooleanPreference(KEY_FIRST_START, true)

    fun setFirstStart(firstStart: Boolean) {
        putBooleanPreference(KEY_FIRST_START, firstStart)
    }

    fun shouldShowGuide(guideId: Int): Boolean =
        getBooleanPreference("preference_gide_$guideId", true)

    fun setGuideShown(guideId: Int, shown: Boolean) {
        putBooleanPreference("preference_gide_$guideId", shown)
    }

    fun isHeadsetControlAllowed(): Boolean =
        getBooleanPreference(KEY_HEADSET_CONTROL_ALLOWED, true)

    fun setHeadsetControlAllowed(enabled: Boolean) {
        putBooleanPreference(KEY_HEADSET_CONTROL_ALLOWED, enabled)
    }

    fun shouldPlayWhenHeadsetConnected(): Boolean =
        getBooleanPreference(KEY_HEADSET_IN_PLAY, false)

    fun setPlayWhenHeadsetConnected(enabled: Boolean) {
        putBooleanPreference(KEY_HEADSET_IN_PLAY, enabled)
    }

    fun shouldStopWhenHeadsetDisconnected(): Boolean =
        getBooleanPreference(KEY_HEADSET_OUT_STOP, true)

    fun setStopWhenHeadsetDisconnected(enabled: Boolean) {
        putBooleanPreference(KEY_HEADSET_OUT_STOP, enabled)
    }

    fun setBluetoothAutoStartEnabled(enabled: Boolean) {
        putBooleanPreference(KEY_BLUETOOTH_AUTO_START, enabled)
    }

    fun setBluetoothAutoStopEnabled(enabled: Boolean) {
        putBooleanPreference(KEY_BLUETOOTH_AUTO_STOP, enabled)
    }

    fun isSlidingSwitchEnabled(): Boolean =
        getBooleanPreference(KEY_SLIDING_SWITCH, true)

    fun setSlidingSwitchEnabled(enabled: Boolean) {
        putBooleanPreference(KEY_SLIDING_SWITCH, enabled)
    }

    fun getInstallVersion(): Int =
        getIntPreference(KEY_INSTALL_VERSION, 100)

    fun isLockPermissionGranted(): Boolean =
        getBooleanPreference(KEY_LOCK_PERMISSION, true)

    fun setLockPermissionGranted(granted: Boolean) {
        putBooleanPreference(KEY_LOCK_PERMISSION, granted)
    }

    fun isDriveWarningEnabled(): Boolean =
        getBooleanPreference(KEY_DRIVE_WARNING, true)

    fun setDriveWarningEnabled(enabled: Boolean) {
        putBooleanPreference(KEY_DRIVE_WARNING, enabled)
    }

    fun shouldUseEnglish(): Boolean =
        getBooleanPreference(KEY_USE_ENGLISH, false)

    private companion object {
        const val KEY_BLUETOOTH_AUTO_START = "preference_bluetooth_auto_start"
        const val KEY_BLUETOOTH_AUTO_STOP = "preference_bluetooth_auto_stop"
        const val KEY_SHORTCUT_PERMISSION = "preference_shortcut_permission"
        const val KEY_FIRST_START = "preference_first_start"
        const val KEY_HEADSET_CONTROL_ALLOWED = "preference_headset_control_allow"
        const val KEY_HEADSET_IN_PLAY = "preference_headset_in_play"
        const val KEY_HEADSET_OUT_STOP = "preference_headset_out_stop"
        const val KEY_SLIDING_SWITCH = "preference_sliding_switch"
        const val KEY_INSTALL_VERSION = "install_version"
        const val KEY_LOCK_PERMISSION = "preference_lock_permission"
        const val KEY_DRIVE_WARNING = "preference_drive_warning"
        const val KEY_USE_ENGLISH = "preference_use_english"
    }
}
