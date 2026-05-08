package gd.app.musicplayer.ui.feature.widget.provider

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetProviderEntryPoint {
    fun widgetUpdateCoordinator(): WidgetUpdateCoordinator
    fun widgetPlaybackSnapshotLoader(): WidgetPlaybackSnapshotLoader
}