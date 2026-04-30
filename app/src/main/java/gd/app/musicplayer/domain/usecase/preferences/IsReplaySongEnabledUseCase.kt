package gd.app.musicplayer.domain.usecase.preferences

import gd.app.musicplayer.util.PreferenceUtil
import javax.inject.Inject

class IsReplaySongEnabledUseCase @Inject constructor(
    private val preferenceUtil: PreferenceUtil
) {
    operator fun invoke(): Boolean = preferenceUtil.isReplaySongEnabled()
}
