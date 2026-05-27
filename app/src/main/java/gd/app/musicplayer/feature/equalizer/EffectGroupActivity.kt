package gd.app.musicplayer.feature.equalizer

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.Color
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.widget.AppCompatTextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applyStatusBarInsetHeight
import gd.app.musicplayer.core.common.extension.navigateBack
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.domain.model.AudioEffectSettings
import gd.app.musicplayer.databinding.ActivityEffectGroupBinding
import gd.app.musicplayer.domain.usecase.equalizer.LoadAudioEffectSettingsUseCase
import gd.app.musicplayer.domain.usecase.equalizer.SaveAudioEffectSettingsUseCase
import gd.app.musicplayer.playback.effects.EffectGroupPreset
import gd.app.musicplayer.playback.effects.EffectGroupPresets
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.core.designsystem.view.SeekBar
import gd.app.musicplayer.core.designsystem.view.SelectBox
import gd.app.musicplayer.feature.player.full.PlayerViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EffectGroupActivity : BaseActivity() {

    @Inject lateinit var loadAudioEffectSettingsUseCase: LoadAudioEffectSettingsUseCase
    @Inject lateinit var saveAudioEffectSettingsUseCase: SaveAudioEffectSettingsUseCase

    private val playerViewModel: PlayerViewModel by viewModels()

    private lateinit var binding: ActivityEffectGroupBinding
    private lateinit var headerController: EffectGroupHeaderController
    private lateinit var adapter: EffectGroupAdapter
    private lateinit var audioManager: AudioManager

    private val volumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            headerController.syncVolume(audioManager)
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivityCompat(Intent(context, EffectGroupActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityEffectGroupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        binding.statusBarSpace.applyStatusBarInsetHeight()
        binding.toolbar.setTitle(R.string.equalizer_sound_effect)
        binding.toolbar.navigateBack(this)

        setupRecycler()
        binding.effectGroupRecycleView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateToolbarOverlay()
            }
        })

        headerController = EffectGroupHeaderController(
            activity = this,
            recyclerView = binding.effectGroupRecycleView,
            onToggleEnabled = ::toggleHeaderEnabled,
            onToggleBoost = ::toggleBoost,
            onVolumeChanged = ::setMusicVolume
        )

        adapter = EffectGroupAdapter(
            layoutInflater = layoutInflater,
            headerView = headerController.view,
            useGridItem = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
            applyTheme = ::applyThemeTo,
            onSelectPreset = ::onPresetClicked
        )
        binding.effectGroupRecycleView.adapter = adapter

        renderState()
        updateToolbarOverlay()
    }

    override fun onResume() {
        super.onResume()
        contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            volumeObserver
        )
        renderState()
    }

    override fun onPause() {
        contentResolver.unregisterContentObserver(volumeObserver)
        super.onPause()
    }

    private fun setupRecycler() {
        val useGrid = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val layoutManager = if (useGrid) {
            GridLayoutManager(this, if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 5 else 3).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return if (position == 0) spanCount else 1
                    }
                }
            }
        } else {
            LinearLayoutManager(this)
        }
        binding.effectGroupRecycleView.layoutManager = layoutManager
    }

    private fun renderState() {
        lifecycleScope.launch {
            renderState(loadAudioEffectSettingsUseCase())
        }
    }

    private fun onPresetClicked(preset: EffectGroupPreset) {
        lifecycleScope.launch {
            val current = loadAudioEffectSettingsUseCase()
            val next = if (current.effectGroupEnabled && current.effectGroupPresetId == preset.id) {
                current.copy(effectGroupEnabled = false)
            } else {
                current.copy(effectGroupEnabled = true, effectGroupPresetId = preset.id)
            }
            saveAndApply(next)
        }
    }

    private fun toggleHeaderEnabled(enabled: Boolean) {
        lifecycleScope.launch {
            val current = loadAudioEffectSettingsUseCase()
            val presetId = current.effectGroupPresetId.takeIf { EffectGroupPresets.find(it) != null } ?: 0
            saveAndApply(current.copy(effectGroupEnabled = enabled, effectGroupPresetId = presetId))
        }
    }

    private fun toggleBoost(enabled: Boolean) {
        lifecycleScope.launch {
            val current = loadAudioEffectSettingsUseCase()
            saveAndApply(
                current.copy(
                    loudnessEnabled = enabled,
                    loudnessStrength = if (enabled) current.loudnessStrength.coerceAtLeast(0.3f) else current.loudnessStrength
                )
            )
        }
    }

    private fun setMusicVolume(volume: Int) {
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            volume.coerceIn(0, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)),
            0
        )
        headerController.syncVolume(audioManager)
    }

    private suspend fun saveAndApply(settings: AudioEffectSettings) {
        saveAudioEffectSettingsUseCase(settings)
        playerViewModel.applyAudioEffects(this)
        renderState(settings)
    }

    private fun renderState(settings: AudioEffectSettings) {
        headerController.render(settings, currentPresetName(settings), audioManager)
        adapter.setSelection(settings.effectGroupEnabled, settings.effectGroupPresetId)
    }

    private fun currentPresetName(settings: AudioEffectSettings): String {
        val preset = EffectGroupPresets.find(settings.effectGroupPresetId) ?: EffectGroupPresets.all.first()
        return getString(preset.nameRes)
    }

    private fun updateToolbarOverlay() {
        val fadeHeight = (binding.toolbarParent.height / 3).coerceAtLeast(1)
        val scrollOffset = binding.effectGroupRecycleView.computeVerticalScrollOffset()
        val alpha = ((scrollOffset.coerceAtMost(fadeHeight) / fadeHeight.toFloat()) * 255f).toInt()
        binding.toolbarParent.setBackgroundColor(Color.argb(alpha, 31, 31, 31))
    }
}

private class EffectGroupHeaderController(
    private val activity: EffectGroupActivity,
    recyclerView: RecyclerView,
    private val onToggleEnabled: (Boolean) -> Unit,
    private val onToggleBoost: (Boolean) -> Unit,
    private val onVolumeChanged: (Int) -> Unit
) {
    val view = activity.layoutInflater.inflate(R.layout.activity_effect_group_header, recyclerView, false)

    private val effectName = view.findViewById<AppCompatTextView>(R.id.effect_group_name)
    private val effectSelect = view.findViewById<SelectBox>(R.id.effect_group_select)
    private val boost = view.findViewById<AppCompatTextView>(R.id.effect_group_boost)
    private val volumeSeek = view.findViewById<SeekBar>(R.id.effect_group_volume_seek)

    init {
        view.findViewById<View>(R.id.status_bar_space).applyStatusBarInsetHeight()
        view.findViewById<View>(R.id.status_bar_space_parent).applyStatusBarInsetHeight()
        activity.applyThemeTo(view)

        effectSelect.setOnSelectChangedListener(object : SelectBox.OnSelectChangedListener {
            override fun onSelectChanged(selectBox: SelectBox, fromUser: Boolean, isSelected: Boolean) {
                if (fromUser) {
                    onToggleEnabled(isSelected)
                }
            }
        })
        volumeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    onVolumeChanged(progress)
                }
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                recyclerView.requestDisallowInterceptTouchEvent(false)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                recyclerView.requestDisallowInterceptTouchEvent(true)
            }
        })
        boost.setOnClickListener {
            val enabled = !it.isSelected
            it.isSelected = enabled
            onToggleBoost(enabled)
        }

        view.findViewById<AppCompatTextView>(R.id.effect_group_tip_1).append(" : ")
        view.findViewById<AppCompatTextView>(R.id.effect_group_tip_2).append(" : ")
    }

    fun render(settings: AudioEffectSettings, currentName: String, audioManager: AudioManager) {
        effectName.text = currentName
        effectName.isSelected = settings.effectGroupEnabled
        effectSelect.isSelected = settings.effectGroupEnabled
        boost.isSelected = settings.loudnessEnabled
        syncVolume(audioManager)
    }

    fun syncVolume(audioManager: AudioManager) {
        if (volumeSeek.isPressed) return
        volumeSeek.setMax(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
        volumeSeek.setProgress(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    }
}

