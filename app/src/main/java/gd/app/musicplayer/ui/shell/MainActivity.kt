package gd.app.musicplayer.ui.shell

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.core.common.extension.externalIntentKey
import gd.app.musicplayer.core.common.extension.extractExternalAudioUris
import gd.app.musicplayer.core.common.extension.isExternalAudioIntent
import gd.app.musicplayer.databinding.ActivityMainBinding
import gd.app.musicplayer.feature.home.MainFragment
import gd.app.musicplayer.core.common.extension.isTablet
import gd.app.musicplayer.core.common.extension.normalizePath
import gd.app.musicplayer.core.common.extension.screenWidth
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.usecase.library.GetTracksUseCase
import gd.app.musicplayer.domain.usecase.scan.SyncMediaStoreLibraryUseCase
import gd.app.musicplayer.playback.PlaybackController
import gd.app.musicplayer.ui.common.base.BasePlayerSheetActivity
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : BasePlayerSheetActivity() {

    companion object {
        private const val STATE_SHOW_MENU = "show_menu"
        const val EXTRA_EXPAND_PLAYER = "gd.app.musicplayer.extra.EXPAND_PLAYER"

        fun start(
            context: Context,
            sourceIntent: Intent? = null
        ) {
            val intent = if (sourceIntent == null) {
                Intent(context, MainActivity::class.java)
            } else {
                Intent(sourceIntent).apply {
                    setClass(context, MainActivity::class.java)
                }
            }
            context.startActivityCompat(intent)
        }
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var navigationDrawer: DrawerLayout

    @Inject lateinit var playbackController: PlaybackController
    @Inject lateinit var getTracksUseCase: GetTracksUseCase
    @Inject lateinit var syncMediaStoreLibraryUseCase: SyncMediaStoreLibraryUseCase

    private var lastHandledExternalIntentKey: String? = null

    override val playerSheet: FrameLayout
        get() = binding.playerSheet

    override val miniPlayer: View
        get() = binding.miniPlayer

    override val fullPlayer: View
        get() = binding.bottomPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeMainUi(savedInstanceState)
        setupPlayerSheet()
        handleExternalAudioIntent(intent)
        handlePlayerSheetIntent(intent)
    }

    fun openDrawer() {
        navigationDrawer.openDrawer(GravityCompat.START)
    }

    fun drawerLayout(): DrawerLayout = navigationDrawer


    private fun initializeMainUi(savedInstanceState: Bundle?) {
        navigationDrawer = binding.mainDrawerLayout

        installBackHandler()
        setupDrawerWidth()
        setupFragments(savedInstanceState)
        setupFragmentCallbacks()
        updateDrawerLockMode()

        if (savedInstanceState?.getBoolean(STATE_SHOW_MENU) == true) {
            binding.root.post { openDrawer() }
        }
    }


    private fun installBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    navigationDrawer.isDrawerOpen(GravityCompat.START) -> {
                        navigationDrawer.closeDrawer(GravityCompat.START)
                    }

                    supportFragmentManager.backStackEntryCount > 0 -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })
    }

    private fun setupDrawerWidth() {
        val drawerWidthRatio = if (isTablet()) 0.4f else 0.8f
        binding.mainMenu.layoutParams = DrawerLayout.LayoutParams(
            (screenWidth * drawerWidthRatio).toInt(),
            DrawerLayout.LayoutParams.MATCH_PARENT
        ).apply {
            gravity = GravityCompat.START
        }
        navigationDrawer.setDrawerElevation(resources.displayMetrics.density * 4f)
    }

    private fun setupFragments(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) return
        supportFragmentManager.beginTransaction()
            .replace(binding.mainMenu.id, MoreFragment(), MoreFragment::class.java.simpleName)
            .replace(
                binding.mainFragmentContainer.id,
                MainFragment(),
                MainFragment::class.java.simpleName
            )
            .commitNow()
    }

    private fun setupFragmentCallbacks() {
        supportFragmentManager.addOnBackStackChangedListener {
            updateDrawerLockMode()
        }
    }

    private fun updateDrawerLockMode() {
        navigationDrawer.setDrawerLockMode(
            if (supportFragmentManager.backStackEntryCount == 0) {
                DrawerLayout.LOCK_MODE_UNLOCKED
            } else {
                DrawerLayout.LOCK_MODE_LOCKED_CLOSED
            }
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_MENU || supportFragmentManager.backStackEntryCount != 0) {
            return super.onKeyDown(keyCode, event)
        }

        return if (navigationDrawer.isDrawerOpen(GravityCompat.START)) {
            navigationDrawer.closeDrawer(GravityCompat.START)
            true
        } else {
            navigationDrawer.openDrawer(GravityCompat.START)
            true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalAudioIntent(intent)
        handlePlayerSheetIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_SHOW_MENU, navigationDrawer.isDrawerOpen(GravityCompat.START))
        super.onSaveInstanceState(outState)
    }

    private fun handleExternalAudioIntent(intent: Intent?) {
        if (!intent.isExternalAudioIntent()) return
        val safeIntent = intent ?: return

        val uris = safeIntent.extractExternalAudioUris()
        if (uris.isEmpty()) return

        val intentKey = safeIntent.externalIntentKey()
        if (intentKey == lastHandledExternalIntentKey) return
        lastHandledExternalIntentKey = intentKey

        lifecycleScope.launch {
            val queue = withContext(Dispatchers.IO) {
                runCatching {
                    // Keep local DB in sync so newly copied files are resolvable.
                    syncMediaStoreLibraryUseCase(incremental = false)
                }

                val allTracks = getTracksUseCase(MusicSet.Tracks)
                uris.mapNotNull { uri ->
                    resolveTrackFromUri(uri, allTracks)
                }
            }

            if (queue.isNotEmpty()) {
                playbackController.playQueue(queue, 0)
            }
        }
    }

    private fun resolveTrackFromUri(
        uri: Uri,
        tracks: List<Music>
    ): Music? {
        val byId = resolveMediaStoreId(uri)
            ?.let { id -> tracks.firstOrNull { track -> track.id == id } }
        if (byId != null) return byId

        val dataPath = resolveDataPath(uri)?.normalizePath()
        if (dataPath.isNullOrBlank()) return null

        return tracks.firstOrNull { track ->
            track.data.normalizePath() == dataPath
        }
    }

    private fun resolveMediaStoreId(uri: Uri): Long? {
        if (uri.scheme == "content" && uri.authority == MediaStore.AUTHORITY) {
            runCatching { ContentUris.parseId(uri) }
                .getOrNull()
                ?.takeIf { it >= 0L }
                ?.let { return it }
        }

        val projection = arrayOf(MediaStore.Audio.Media._ID)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
            if (idIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getLong(idIndex)
            }
        }
        return null
    }

    private fun resolveDataPath(uri: Uri): String? {
        if (uri.scheme == "file") return uri.path

        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val dataIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            if (dataIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(dataIndex)
            }
        }
        return null
    }
}
