package gd.app.musicplayer.domain.usecase.preferences

import gd.app.musicplayer.util.PreferenceUtil
import javax.inject.Inject

class GetQueueForSearchingModeUseCase @Inject constructor(
    private val preferenceUtil: PreferenceUtil
) {
    operator fun invoke(): Int = preferenceUtil.getQueueForSearchingMode()
}
