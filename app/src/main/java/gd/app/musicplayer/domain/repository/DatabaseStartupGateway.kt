package gd.app.musicplayer.domain.repository

interface DatabaseStartupGateway {
    suspend fun reseedEffectPresets()

    suspend fun importAllMusic()
}
