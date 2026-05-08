package gd.app.musicplayer.ui.feature.widget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import gd.app.musicplayer.di.WidgetConfigDataStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class WidgetConfigStore @Inject constructor(
    @WidgetConfigDataStore
    private val dataStore: DataStore<Preferences>
) {

    suspend fun load(
        appWidgetId: Int,
        classify: String
    ): WidgetConfig {
        val defaultStyle = WidgetCatalog.defaultStyle(classify)
        val defaultTheme = WidgetCatalog.defaultThemeOption()
        val preferences = dataStore.data.first()

        return WidgetConfig(
            classify = classify,
            styleKey = preferences[stringPreferencesKey(key(KEY_STYLE, appWidgetId))]
                ?: defaultStyle.styleKey,
            themeType = preferences[intPreferencesKey(key(KEY_THEME_TYPE, appWidgetId))]
                ?: defaultTheme.themeType,
            themeIndex = preferences[intPreferencesKey(key(KEY_THEME_INDEX, appWidgetId))]
                ?: defaultTheme.index,
            alpha = preferences[floatPreferencesKey(key(KEY_ALPHA, appWidgetId))]
                ?: defaultTheme.alpha
        )
    }

    suspend fun save(
        appWidgetId: Int,
        config: WidgetConfig
    ) {
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey(key(KEY_CLASSIFY, appWidgetId))] = config.classify
            preferences[stringPreferencesKey(key(KEY_STYLE, appWidgetId))] = config.styleKey
            preferences[intPreferencesKey(key(KEY_THEME_TYPE, appWidgetId))] = config.themeType
            preferences[intPreferencesKey(key(KEY_THEME_INDEX, appWidgetId))] = config.themeIndex
            preferences[floatPreferencesKey(key(KEY_ALPHA, appWidgetId))] = config.alpha
        }
    }

    suspend fun delete(appWidgetId: Int) {
        dataStore.edit { preferences ->
            preferences.remove(stringPreferencesKey(key(KEY_CLASSIFY, appWidgetId)))
            preferences.remove(stringPreferencesKey(key(KEY_STYLE, appWidgetId)))
            preferences.remove(intPreferencesKey(key(KEY_THEME_TYPE, appWidgetId)))
            preferences.remove(intPreferencesKey(key(KEY_THEME_INDEX, appWidgetId)))
            preferences.remove(floatPreferencesKey(key(KEY_ALPHA, appWidgetId)))
        }
    }

    suspend fun loadClassify(appWidgetId: Int): String? {
        return dataStore.data.first()[stringPreferencesKey(key(KEY_CLASSIFY, appWidgetId))]
    }

    private fun key(
        prefix: String,
        appWidgetId: Int
    ): String {
        return "${prefix}_$appWidgetId"
    }

    private companion object {
        const val KEY_CLASSIFY = "widget_classify"
        const val KEY_STYLE = "widget_style"
        const val KEY_THEME_TYPE = "widget_theme_type"
        const val KEY_THEME_INDEX = "widget_theme_index"
        const val KEY_ALPHA = "widget_alpha"
    }
}