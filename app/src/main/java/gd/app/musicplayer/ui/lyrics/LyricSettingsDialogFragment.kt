package gd.app.musicplayer.ui.lyrics

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
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.designsystem.dialog.BaseBottomSheetDialogFragment
import gd.app.musicplayer.core.designsystem.view.ColorSelectView
import gd.app.musicplayer.core.designsystem.view.SeekBar
import gd.app.musicplayer.core.designsystem.view.SelectBox
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.data.local.preference.DesktopLyricPreference
import gd.app.musicplayer.data.local.preference.DesktopLyricPreferenceStore
import gd.app.musicplayer.data.local.preference.LyricSettingPreferenceStore
import gd.app.musicplayer.data.local.preference.LyricsSettingPreference
import gd.app.musicplayer.databinding.DialogLyricSettingBinding
import gd.app.musicplayer.ui.theme.ThemeTags
import gd.app.musicplayer.util.TrackLyricsStore
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LyricSettingsDialogFragment : BaseBottomSheetDialogFragment(), View.OnClickListener {

    @Inject lateinit var lyricSettingPreferenceStore: LyricSettingPreferenceStore

    @Inject lateinit var desktopLyricPreferenceStore: DesktopLyricPreferenceStore

    private var _binding: DialogLyricSettingBinding? = null
    private val binding: DialogLyricSettingBinding
        get() = checkNotNull(_binding)

    private var lyricPreference = LyricsSettingPreference()
    private var desktopLyricPreference = DesktopLyricPreference()

    private val hasTimedLyrics: Boolean
        get() = requireArguments().getBoolean(ARG_HAS_TIMED_LYRICS)

    private val trackId: Long
        get() = requireArguments().getLong(ARG_TRACK_ID)

    override fun onCreateBottomSheetView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogLyricSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.lyricSizeMinus.setOnClickListener(this)
        binding.lyricSizePlus.setOnClickListener(this)
        binding.lyricDeskSelect.setOnClickListener(this)
        binding.lyricAdjustSettings.setOnClickListener(this)

        viewLifecycleOwner.lifecycleScope.launch {
            lyricPreference = lyricSettingPreferenceStore.getLyricsPreference()
            desktopLyricPreference = desktopLyricPreferenceStore.getPreferenceSnapshot()

            setupLyricControls()
            syncContentVisibility()
            syncDesktopLyricsToggle()
            renderAdjustOffset()
        }
    }

    override fun onResume() {
        super.onResume()

        viewLifecycleOwner.lifecycleScope.launch {
            desktopLyricPreference = desktopLyricPreferenceStore.getPreferenceSnapshot()
            syncDesktopLyricsToggle()
            renderAdjustOffset()
        }
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

            R.id.lyric_desk_select -> {
                toggleDesktopLyrics()
            }

            R.id.lyric_adjust_settings -> {
                dismissAllowingStateLoss()

                LyricAdjustDialogFragment
                    .newInstance(trackId)
                    .show(parentFragmentManager, LyricAdjustDialogFragment.TAG)
            }
        }
    }

    private fun setupLyricControls() {
        binding.lyricSeekBar.setMax(8)
        binding.lyricSeekBar.setProgress(
            lyricPreference.lyricTextSize.toInt().coerceIn(14, 22) - 14
        )

        binding.lyricSeekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (!fromUser) return
                    updateLyricTextSize(progress + 14)
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            }
        )

        binding.lyricColorSelect.setColor(lyricPreference.lyricColor)
        binding.lyricColorSelect.setOnColorChangedListener(
            ColorSelectView.OnColorChangedListener { color ->
                lyricPreference = lyricPreference.copy(
                    lyricColor = color
                )

                viewLifecycleOwner.lifecycleScope.launch {
                    lyricSettingPreferenceStore.setLyricColor(color)
                    notifyUpdated()
                }
            }
        )

        binding.lyricAutoScrollSelect.setSelected(
            lyricPreference.lyricAutoScrollEnabled
        )

        binding.lyricAutoScrollSelect.setOnSelectChangedListener(
            object : SelectBox.OnSelectChangedListener {
                override fun onSelectChanged(
                    selectBox: SelectBox,
                    fromUser: Boolean,
                    isSelected: Boolean
                ) {
                    if (!fromUser) return

                    lyricPreference = lyricPreference.copy(
                        lyricAutoScrollEnabled = isSelected
                    )

                    viewLifecycleOwner.lifecycleScope.launch {
                        lyricSettingPreferenceStore.setLyricAutoScrollEnabled(isSelected)

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
            }
        )

        binding.lyricFeedback.visibility = View.GONE

        bindTaggedButtonGroup(
            container = binding.lyricAlignLayout,
            tag = ThemeTags.LYRIC_ALIGN_BUTTON,
            selectedIndex = lyricPreference.lyricAlign
        ) { index ->
            lyricPreference = lyricPreference.copy(
                lyricAlign = index
            )

            viewLifecycleOwner.lifecycleScope.launch {
                lyricSettingPreferenceStore.setLyricAlign(index)
                notifyUpdated()
            }
        }

        bindTaggedButtonGroup(
            container = binding.lyricAlignLayout,
            tag = ThemeTags.LYRIC_TYPEFACE_BUTTON,
            selectedIndex = lyricPreference.lyricStyle
        ) { index ->
            lyricPreference = lyricPreference.copy(
                lyricStyle = index
            )

            viewLifecycleOwner.lifecycleScope.launch {
                lyricSettingPreferenceStore.setLyricStyle(index)
                notifyUpdated()
            }
        }
    }

    private fun updateLyricTextSize(size: Int) {
        val normalizedSize = size.coerceIn(14, 22)

        if (normalizedSize == lyricPreference.lyricTextSize.toInt()) {
            return
        }

        lyricPreference = lyricPreference.copy(
            lyricTextSize = normalizedSize.toFloat()
        )

        viewLifecycleOwner.lifecycleScope.launch {
            lyricSettingPreferenceStore.setLyricTextSize(normalizedSize.toFloat())
            notifyUpdated()
        }
    }

    private fun bindTaggedButtonGroup(
        container: ViewGroup,
        tag: String,
        selectedIndex: Int,
        onSelected: (Int) -> Unit
    ) {
        val buttons = mutableListOf<View>()

        collectTaggedChildren(
            root = container,
            tag = tag,
            target = buttons
        )

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

    private fun collectTaggedChildren(
        root: View,
        tag: String,
        target: MutableList<View>
    ) {
        if (root.tag == tag) {
            target += root
        }

        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                collectTaggedChildren(
                    root = root.getChildAt(index),
                    tag = tag,
                    target = target
                )
            }
        }
    }

    private fun syncContentVisibility() {
        binding.lyricAutoScrollLayout.visibility =
            if (hasTimedLyrics) View.GONE else View.VISIBLE

        binding.lyricColorLayout.visibility =
            if (hasTimedLyrics) View.VISIBLE else View.GONE

        binding.lyricAdjustSettings.visibility =
            if (hasTimedLyrics) View.VISIBLE else View.GONE
    }

    private fun renderAdjustOffset() {
        val offsetMs = TrackLyricsStore
            .from(requireContext())
            .getTrackLyricOffset(trackId)

        binding.lyricAdjustText.text = when {
            offsetMs == 0 -> {
                getString(R.string.lrc_time_normal)
            }

            offsetMs > 0 -> {
                "+${offsetMs / 1000f}s"
            }

            else -> {
                "${offsetMs / 1000f}s"
            }
        }
    }

    private fun syncDesktopLyricsToggle() {
        binding.lyricDeskSelect.isSelected =
            desktopLyricPreference.visible && hasOverlayPermission()
    }

    private fun toggleDesktopLyrics() {
        if (desktopLyricPreference.visible) {
            desktopLyricPreference = desktopLyricPreference.copy(
                visible = false,
                pendingEnableAfterPermission = false
            )

            viewLifecycleOwner.lifecycleScope.launch {
                desktopLyricPreferenceStore.updatePreference(
                    visible = false,
                    pendingEnableAfterPermission = false
                )
                syncDesktopLyricsToggle()
            }

            return
        }

        if (!hasOverlayPermission()) {
            desktopLyricPreference = desktopLyricPreference.copy(
                pendingEnableAfterPermission = true
            )

            viewLifecycleOwner.lifecycleScope.launch {
                desktopLyricPreferenceStore.setPendingEnableAfterPermission(true)
            }

            ToastUtil.show(requireContext(), R.string.float_window_permission_tip)
            openOverlayPermissionSettings()
            return
        }

        desktopLyricPreference = desktopLyricPreference.copy(
            visible = true,
            pendingEnableAfterPermission = false
        )

        viewLifecycleOwner.lifecycleScope.launch {
            desktopLyricPreferenceStore.updatePreference(
                visible = true,
                pendingEnableAfterPermission = false
            )
            syncDesktopLyricsToggle()
        }
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

        fun newInstance(
            trackId: Long,
            hasTimedLyrics: Boolean
        ): LyricSettingsDialogFragment {
            return LyricSettingsDialogFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TRACK_ID, trackId)
                    putBoolean(ARG_HAS_TIMED_LYRICS, hasTimedLyrics)
                }
            }
        }
    }
}

