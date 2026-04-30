package gd.app.musicplayer.domain.usecase.preferences

import gd.app.musicplayer.data.repo.UserPreferencesRepo
import kotlinx.coroutines.flow.Flow

class ObservePreferenceChangesUseCase(
    private val preferencesRepo: UserPreferencesRepo
) {
    operator fun invoke(vararg keys: String): Flow<Unit> = preferencesRepo.observePreferenceChanges(*keys)
}

