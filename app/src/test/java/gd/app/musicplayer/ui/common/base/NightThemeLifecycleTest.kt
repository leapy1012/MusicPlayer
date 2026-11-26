package gd.app.musicplayer.ui.common.base

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.widget.FrameLayout
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.coui.appcompat.preference.COUIPreferenceFragment
import com.coui.appcompat.preference.COUISwitchPreference
import dagger.hilt.internal.GeneratedComponent
import dagger.hilt.internal.GeneratedComponentManager
import gd.app.musicplayer.R
import gd.app.musicplayer.core.datastore.ThemeSettingPreferenceStore
import gd.app.musicplayer.core.designsystem.theme.*
import gd.app.musicplayer.domain.repository.ThemeRepo
import gd.app.musicplayer.ui.theme.ThemeEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=NightThemeTestApp::class, manifest=Config.NONE, shadows=[NoAccentLoaderShadow::class])
@LooperMode(LooperMode.Mode.PAUSED)
class NightThemeLifecycleTest {
    @Test fun restoredPreferencesAreConstructedWithTheNewThemeBeforeBinding() {
        val app=(RuntimeEnvironment.getApplication() as NightThemeTestApp)
        val controller=Robolectric.buildActivity(NightThemeTestActivity::class.java).setup()
        val light=NightThemeTestPreferences.createdTitleColor
        val state=Bundle()
        controller.pause().saveInstanceState(state).stop().destroy()
        app.provider.palette=DarkThemePalette()
        val restored=Robolectric.buildActivity(NightThemeTestActivity::class.java)
            .create(state).start().restoreInstanceState(state).resume().visible()
        val dark=NightThemeTestPreferences.createdTitleColor
        assertNotEquals(light,dark)
        assertEquals(resolveTitleColor(restored.get()),dark)
        assertNotNull(restored.get().supportFragmentManager.findFragmentByTag("prefs"))
        restored.pause().stop().destroy()
        app.scope.cancel()
    }

    @Test @Config(qualifiers="night")
    fun whiteCardDoesNotOverrideSystemNightResources() {
        val app=RuntimeEnvironment.getApplication() as NightThemeTestApp
        assertEquals(android.content.res.Configuration.UI_MODE_NIGHT_YES,
            app.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)
        val controller=Robolectric.buildActivity(NightThemeTestActivity::class.java).setup()
        assertEquals(android.content.res.Configuration.UI_MODE_NIGHT_YES,
            controller.get().resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)
        assertEquals(resolveTitleColor(controller.get()),NightThemeTestPreferences.createdTitleColor)
        controller.pause().stop().destroy()
        app.scope.cancel()
    }

    @Test fun nightChangesCoalesceAndWaitForResumeButAccentChangesDoNotRecreate() {
        val app=(RuntimeEnvironment.getApplication() as NightThemeTestApp)
        val controller=Robolectric.buildActivity(NightThemeTestActivity::class.java).setup()
        val activity=controller.get()
        app.provider.palette=DarkThemePalette()
        repeat(3) { activity.onThemeChanged(app.provider.palette) }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1,activity.recreations)
        controller.pause().stop()
        repeat(3) { activity.onThemeChanged(app.provider.palette) }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1,activity.recreations)
        controller.start().resume()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2,activity.recreations)
        // The test intercepts recreate; restore the matching light palette first.
        app.provider.palette=LightThemePalette().apply { setAccentColor(0xffe03080.toInt()) }
        activity.onThemeChanged(app.provider.palette)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2,activity.recreations)
        controller.pause().stop().destroy()
        app.scope.cancel()
    }
}

private fun resolveTitleColor(context: Context):Int {
    val a=context.obtainStyledAttributes(intArrayOf(com.coui.appcompat.R.attr.couiColorLabelPrimary))
    return try { a.getColor(0,0) } finally { a.recycle() }
}

class NightThemeTestApp:Application(),GeneratedComponentManager<NightThemeTestComponent> {
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    val provider=NightThemeTestProvider()
    lateinit var registry:ThemeRegistry
    lateinit var repo:ThemeRepo
    override fun onCreate() {
        super.onCreate()
        val store=ThemeSettingPreferenceStore(object:DataStore<Preferences> {
            override val data=flowOf(androidx.datastore.preferences.core.preferencesOf(
                androidx.datastore.preferences.core.intPreferencesKey("theme_type") to 1))
            override suspend fun updateData(transform:suspend (Preferences)->Preferences)=transform(emptyPreferences())
        })
        val loader=ThemeBitmapLoader()
        registry=ThemeRegistry(DefaultThemeProvider(loader)).apply { installProvider(provider,true) }
        val manager=ThemeManager(this,store,registry,loader,scope)
        repo=ThemeRepo(this,registry,manager,store)
    }
    override fun generatedComponent()=NightThemeTestComponent(this)
}
class NightThemeTestComponent(private val app:NightThemeTestApp):GeneratedComponent,ActivityThemeEntryPoint {
    override fun themeRepo()=app.repo
}
class NightThemeTestProvider:ThemeProvider {
    var palette:ThemePalette=LightThemePalette()
    override fun getCurrentTheme()=palette
    override fun refreshTheme(context:Context) = Unit
    override fun getThemeBinder():ThemeViewBinder?=null
    override fun applyTheme(palette:ThemePalette) { this.palette=palette }
}
class NightThemeTestActivity:BaseActivity() {
    var recreations=0
    override fun onCreate(savedInstanceState:Bundle?) {
        val app=application as NightThemeTestApp
        themeRepo=app.repo
        themeRegistry=app.registry
        themeEngine=ThemeEngine(app.registry)
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this).apply { id=12345 })
        if(savedInstanceState==null) supportFragmentManager.beginTransaction()
            .replace(12345,NightThemeTestPreferences(),"prefs").commitNow()
    }
    override fun recreate() { recreations++ }
}
class NightThemeTestPreferences:COUIPreferenceFragment() {
    override fun onCreatePreferences(state:Bundle?,rootKey:String?) {
        createdTitleColor=resolveTitleColor(requireContext())
        preferenceScreen=preferenceManager.createPreferenceScreen(requireContext()).apply {
            addPreference(COUISwitchPreference(requireContext()).apply { key="night"; title="Night mode" })
        }
    }
    companion object { var createdTitleColor=0 }
}

// Robolectric cannot rebuild its native Theme handles after Resources.addLoaders.
// Exercise real COUI neutral styles here; exact accent loading is checked on D960.
@Implements(className="com.google.android.material.color.ColorResourcesLoaderCreator", isInAndroidSdk=false)
class NoAccentLoaderShadow {
    companion object {
        @JvmStatic @Implementation
        fun create(context:Context, colors:Map<Int,Int>):android.content.res.loader.ResourcesLoader?=null
    }
}
