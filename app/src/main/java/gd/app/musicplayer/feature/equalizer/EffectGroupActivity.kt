package gd.app.musicplayer.feature.equalizer

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.widget.AppCompatImageView
import androidx.lifecycle.lifecycleScope
import com.coui.appcompat.chip.COUIChip
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applyStatusBarInsetHeight
import gd.app.musicplayer.core.common.extension.installCouiPressFeedback
import gd.app.musicplayer.core.common.extension.navigateBack
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.databinding.ActivityEffectGroupBinding
import gd.app.musicplayer.domain.model.AudioEffectSettings
import gd.app.musicplayer.domain.usecase.equalizer.LoadAudioEffectSettingsUseCase
import gd.app.musicplayer.domain.usecase.equalizer.SaveAudioEffectSettingsUseCase
import gd.app.musicplayer.feature.player.full.PlayerViewModel
import gd.app.musicplayer.playback.effects.EffectGroupPreset
import gd.app.musicplayer.playback.effects.EffectGroupPresets
import gd.app.musicplayer.ui.common.base.BaseActivity
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EffectGroupActivity : BaseActivity() {

    @Inject lateinit var loadAudioEffectSettingsUseCase: LoadAudioEffectSettingsUseCase
    @Inject lateinit var saveAudioEffectSettingsUseCase: SaveAudioEffectSettingsUseCase

    private val playerViewModel: PlayerViewModel by viewModels()

    private lateinit var binding: ActivityEffectGroupBinding
    private lateinit var audioManager: AudioManager
    private val presetRows = mutableListOf<PresetRow>()
    private var isRendering = false
    private var volumeTracking = false

    private val volumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            syncVolume()
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
        binding.toolbar.navigateBack(this)
        binding.appBar.bringToFront()

        setupHeader()
        setupPresets()
        renderState()
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

    private fun setupHeader() = with(binding) {
        effectGroupEnabledRow.bindSwitchRow(effectGroupSelect)
        effectGroupSelect.setOnCheckedChangeListener { _, isChecked ->
            if (!isRendering) toggleHeaderEnabled(isChecked)
        }
        effectGroupBoost.setOnClickListener { toggleBoost(effectGroupBoost.isChecked) }
        effectGroupVolumeSeek.setOnSliderChangeListener(
            onTrackingChanged = { tracking ->
                volumeTracking = tracking
                if (!tracking) syncVolume()
            }
        ) { progress, fromUser ->
            if (fromUser && !isRendering) setMusicVolume(progress)
        }
    }

    private fun setupPresets() {
        val container = binding.effectGroupPresets
        container.clipToOutline = true
        EffectGroupPresets.all.forEachIndexed { index, preset ->
            if (index > 0) {
                layoutInflater.inflate(R.layout.activity_effect_group_item_divider, container, true)
            }
            val view = layoutInflater.inflate(R.layout.activity_effect_group_item, container, false)
            container.addView(view)
            presetRows += PresetRow(view, preset)
        }
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
    }

    private suspend fun saveAndApply(settings: AudioEffectSettings) {
        saveAudioEffectSettingsUseCase(settings)
        playerViewModel.applyAudioEffects(this)
        renderState(settings)
    }

    private fun renderState(settings: AudioEffectSettings) {
        isRendering = true
        binding.effectGroupName.text = currentPresetName(settings)
        binding.effectGroupSelect.isChecked = settings.effectGroupEnabled
        binding.effectGroupBoost.isChecked = settings.loudnessEnabled
        presetRows.forEach {
            it.render(settings.effectGroupEnabled && settings.effectGroupPresetId == it.preset.id)
        }
        isRendering = false
        syncVolume()
    }

    private fun syncVolume() {
        if (volumeTracking) return
        isRendering = true
        binding.effectGroupVolumeSeek.max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        binding.effectGroupVolumeSeek.progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        isRendering = false
    }

    private fun currentPresetName(settings: AudioEffectSettings): String {
        val preset = EffectGroupPresets.find(settings.effectGroupPresetId) ?: EffectGroupPresets.all.first()
        return getString(preset.nameRes)
    }

    private inner class PresetRow(private val view: View, val preset: EffectGroupPreset) {
        private val use = view.findViewById<COUIChip>(R.id.group_effect_item_use)

        init {
            view.findViewById<AppCompatImageView>(R.id.group_effect_item_icon).setImageResource(preset.iconRes)
            view.findViewById<TextView>(R.id.group_effect_item_name).setText(preset.nameRes)
            view.installCouiPressFeedback()
            view.setOnClickListener { onPresetClicked(preset) }
            use.setOnClickListener { onPresetClicked(preset) }
        }

        fun render(inUse: Boolean) {
            use.isChecked = inUse
            use.setText(if (inUse) R.string.in_use else R.string.use)
        }
    }
}
