package gd.app.musicplayer.ui.feature.lyrics

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.setFragmentResult
import gd.app.musicplayer.R
import gd.app.musicplayer.databinding.DialogLyricSettingBinding
import gd.app.musicplayer.core.ui.dialog.BaseDialogFragment
import gd.app.musicplayer.core.ui.view.ColorSelectView
import gd.app.musicplayer.core.ui.view.SeekBar
import gd.app.musicplayer.core.ui.view.SelectBox
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.util.PreferenceUtil
import gd.app.musicplayer.util.TrackLyricsStore

class LyricSettingsDialogFragment : BaseDialogFragment(), View.OnClickListener {

    private var _binding: DialogLyricSettingBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val preferences by lazy { PreferenceUtil.getInstance(requireContext()) }

    private val hasTimedLyrics: Boolean
        get() = requireArguments().getBoolean(ARG_HAS_TIMED_LYRICS)

    private val trackId: Long
        get() = requireArguments().getLong(ARG_TRACK_ID)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogLyricSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyDialogWidth(0.95f)
        applyDialogBackground(view)

        binding.lyricSizeMinus.setOnClickListener(this)
        binding.lyricSizePlus.setOnClickListener(this)
        binding.lyricDeskSelect.setOnClickListener(this)
        binding.lyricAdjustSettings.setOnClickListener(this)

        binding.lyricSeekBar.setMax(8)
        binding.lyricSeekBar.setProgress(preferences.getLyricTextSize().coerceIn(14, 22) - 14)
        binding.lyricSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                updateLyricTextSize(progress + 14)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
        })

        binding.lyricColorSelect.setColor(preferences.getLyricColor())
        binding.lyricColorSelect.setOnColorChangedListener(
            ColorSelectView.OnColorChangedListener { color ->
                preferences.setLyricColor(color)
                notifyUpdated()
            }
        )

        binding.lyricAutoScrollSelect.setSelected(preferences.isLyricAutoScrollEnabled())
        binding.lyricAutoScrollSelect.setOnSelectChangedListener(
            object : SelectBox.OnSelectChangedListener {
                override fun onSelectChanged(
                    selectBox: SelectBox,
                    fromUser: Boolean,
                    isSelected: Boolean
                ) {
                    if (!fromUser) return
                    preferences.setLyricAutoScrollEnabled(isSelected)
                    ToastUtil.show(
                        requireContext(),
                        if (isSelected) {
                            R.string.lyric_auto_scroll_on
                        } else {
                            R.string.lyric_auto_scroll_off
                        }
                    )
                    notifyUpdated()
                }
            }
        )

        binding.lyricFeedback.visibility = View.GONE

        bindTaggedButtonGroup(
            container = binding.lyricAlignLayout,
            tag = "alignButton",
            selectedIndex = preferences.getLyricAlign()
        ) { index ->
            preferences.setLyricAlign(index)
            notifyUpdated()
        }
        bindTaggedButtonGroup(
            container = binding.lyricAlignLayout,
            tag = "typefaceButton",
            selectedIndex = preferences.getLyricStyle()
        ) { index ->
            preferences.setLyricStyle(index)
            notifyUpdated()
        }

        syncContentVisibility()
        syncDesktopLyricsToggle()
        renderAdjustOffset()
    }

    override fun onResume() {
        super.onResume()
        syncDesktopLyricsToggle()
        renderAdjustOffset()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.lyric_size_minus -> {
                val progress = binding.lyricSeekBar.getProgress()
                if (progress <= 0) return
                binding.lyricSeekBar.setProgress(progress - 1)
                updateLyricTextSize(progress + 13)
            }

            R.id.lyric_size_plus -> {
                val progress = binding.lyricSeekBar.getProgress()
                if (progress >= binding.lyricSeekBar.getMax()) return
                binding.lyricSeekBar.setProgress(progress + 1)
                updateLyricTextSize(progress + 15)
            }

            R.id.lyric_desk_select -> toggleDesktopLyrics()
            R.id.lyric_adjust_settings -> {
                dismissAllowingStateLoss()
                LyricAdjustDialogFragment.newInstance(trackId)
                    .show(parentFragmentManager, LyricAdjustDialogFragment.TAG)
            }
        }
    }

    private fun updateLyricTextSize(size: Int) {
        if (size == preferences.getLyricTextSize()) return
        preferences.setLyricTextSize(size)
        notifyUpdated()
    }

    private fun bindTaggedButtonGroup(
        container: ViewGroup,
        tag: String,
        selectedIndex: Int,
        onSelected: (Int) -> Unit
    ) {
        val buttons = mutableListOf<View>()
        collectTaggedChildren(container, tag, buttons)
        buttons.forEachIndexed { index, child ->
            child.isSelected = index == selectedIndex
            child.setOnClickListener {
                buttons.forEachIndexed { innerIndex, innerChild ->
                    innerChild.isSelected = innerIndex == index
                }
                onSelected(index)
            }
        }
    }

    private fun collectTaggedChildren(root: View, tag: String, target: MutableList<View>) {
        if (root.tag == tag) {
            target += root
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                collectTaggedChildren(root.getChildAt(index), tag, target)
            }
        }
    }

    private fun syncContentVisibility() {
        binding.lyricAutoScrollLayout.visibility = if (hasTimedLyrics) View.GONE else View.VISIBLE
        binding.lyricColorLayout.visibility = if (hasTimedLyrics) View.VISIBLE else View.GONE
        binding.lyricAdjustSettings.visibility = if (hasTimedLyrics) View.VISIBLE else View.GONE
    }

    private fun renderAdjustOffset() {
        val offsetMs = TrackLyricsStore.from(requireContext()).getTrackLyricOffset(trackId)
        binding.lyricAdjustText.text = when {
            offsetMs == 0 -> getString(R.string.lrc_time_normal)
            offsetMs > 0 -> "+${offsetMs / 1000f}s"
            else -> "${offsetMs / 1000f}s"
        }
    }

    private fun syncDesktopLyricsToggle() {
        binding.lyricDeskSelect.isSelected =
            preferences.isDesktopLyricsVisible() && hasOverlayPermission()
    }

    private fun toggleDesktopLyrics() {
        if (preferences.isDesktopLyricsVisible()) {
            preferences.setDesktopLyricsVisible(false)
            syncDesktopLyricsToggle()
            return
        }
        if (!hasOverlayPermission()) {
            ToastUtil.show(requireContext(), R.string.float_window_permission_tip)
            openOverlayPermissionSettings()
            return
        }
        preferences.setDesktopLyricsVisible(true)
        syncDesktopLyricsToggle()
    }

    private fun notifyUpdated() {
        setFragmentResult(RESULT_KEY, Bundle.EMPTY)
    }

    private fun hasOverlayPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            Settings.canDrawOverlays(requireContext())
    }

    private fun openOverlayPermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${requireContext().packageName}")
        )
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            ToastUtil.show(requireContext(), R.string.permission_open_failed)
        }
    }

    companion object {
        const val TAG = "LyricSettingsDialog"
        const val RESULT_KEY = "lyric_settings_result"
        private const val ARG_HAS_TIMED_LYRICS = "has_timed_lyrics"
        private const val ARG_TRACK_ID = "track_id"

        fun newInstance(trackId: Long, hasTimedLyrics: Boolean): LyricSettingsDialogFragment {
            return LyricSettingsDialogFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TRACK_ID, trackId)
                    putBoolean(ARG_HAS_TIMED_LYRICS, hasTimedLyrics)
                }
            }
        }
    }
}
