package gd.app.musicplayer.feature.lyrics

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.appcompat.widget.AppCompatImageView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.coui.appcompat.couiswitch.COUISwitch
import com.coui.appcompat.seekbar.COUISeekBar
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.core.designsystem.theme.accentColor
import gd.app.musicplayer.core.designsystem.dialog.DialogRegistry
import gd.app.musicplayer.core.designsystem.dialog.MaterialDialogConfigFactory
import gd.app.musicplayer.core.designsystem.dialog.MessageDialog
import gd.app.musicplayer.core.designsystem.dialog.OptionsListDialog
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.core.datastore.StatusBarLyricPreference
import gd.app.musicplayer.core.datastore.StatusBarLyricPreferenceStore
import gd.app.musicplayer.databinding.ActivityStatusBarLyricsBinding
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.inflateThemedMenu
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import gd.app.musicplayer.ui.common.hostStaticContent
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class StatusBarLyricsActivity : BaseActivity(), COUISeekBar.OnSeekBarChangeListener {

    @Inject lateinit var materialDialogConfigFactory: MaterialDialogConfigFactory

    @Inject lateinit var statusBarLyricPreferenceStore: StatusBarLyricPreferenceStore

    private lateinit var binding: ActivityStatusBarLyricsBinding
    private lateinit var colorAdapter: ColorAdapter
    private lateinit var colorLayoutManager: LinearLayoutManager

    private var currentPreference = StatusBarLyricPreference()
    private var suppressSeekBarCallback = false
    private var trackingSeekBar: COUISeekBar? = null
    private var seekBarPersistJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityStatusBarLyricsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupContent()
        setupPreferences()
        setupSeekBars()
        setupColorList()
        observePreference()
    }

    override fun onResume() {
        super.onResume()
        syncOverlayPermissionState()
    }

    override fun onDestroy() {
        seekBarPersistJob?.cancel()
        super.onDestroy()
    }

    override fun onProgressChanged(
        seekBar: COUISeekBar,
        progress: Int,
        fromUser: Boolean
    ) {
        if (!fromUser || suppressSeekBarCallback) return

        val max = seekBar.max.coerceAtLeast(1)
        val ratio = progress.toFloat() / max.toFloat()

        currentPreference = currentPreference.withSeekRatio(seekBar, ratio)
        scheduleSeekBarPersist(seekBar, ratio)
    }

    override fun onStopTrackingTouch(seekBar: COUISeekBar) {
        trackingSeekBar = null
        binding.settingList.requestDisallowInterceptTouchEvent(false)

        val max = seekBar.max.coerceAtLeast(1)
        val ratio = seekBar.progress.toFloat() / max.toFloat()

        seekBarPersistJob?.cancel()
        seekBarPersistJob = lifecycleScope.launch {
            persistSeekRatio(seekBar, ratio)
        }
    }

    override fun onStartTrackingTouch(seekBar: COUISeekBar) {
        trackingSeekBar = seekBar
        binding.settingList.requestDisallowInterceptTouchEvent(true)
    }

    private fun setupToolbar() {
        setupEdgeToEdgeToolbar(
            root = binding.root,
            statusBarView = binding.statusBarSpace,
            bottomPaddingView = binding.root,
            toolbar = binding.toolbar,
            titleRes = R.string.sbar_lyric
        )

        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Not app:menu — COUIToolbar builds that menu view before its click listener exists.
        binding.toolbar.inflateThemedMenu(R.menu.menu_activity_sbar_lyric, ::applyThemeTo)
        binding.toolbar.setOnMenuItemClickListener { item: MenuItem ->
            if (item.itemId == R.id.menu_reset) {
                showResetDialog()
            }
            true
        }
    }

    private fun setupContent() {
        binding.settingList.hostStaticContent(binding.settingContent)
        // Keeps the rows' press feedback inside the rounded card corners.
        listOf(
            binding.preferenceSbarLyricContainer1,
            binding.preferenceSbarLyricContainer2,
            binding.preferenceSbarLyricContainer3
        ).forEach { card -> card.clipToOutline = true }
    }

    private fun setupPreferences() {
        binding.preferenceSbarLyricEnable.setOnClickListener {
            handleEnableToggle()
        }

        binding.preferenceSbarLyricShowPaused.setOnClickListener {
            val next = !currentPreference.showPaused
            lifecycleScope.launch {
                statusBarLyricPreferenceStore.setShowPaused(next)
            }
        }

        binding.preferenceSbarLyricClickable.setOnClickListener {
            val next = !currentPreference.clickable
            lifecycleScope.launch {
                statusBarLyricPreferenceStore.setClickable(next)
            }
        }

        binding.preferenceSbarLyricContent.setOnClickListener {
            showContentChoiceDialog()
        }

        binding.preferenceSbarLyricGravity.setOnClickListener {
            showGravityChoiceDialog()
        }
    }

    private fun setupSeekBars() {
        listOf(
            binding.sbarLyricXSeek,
            binding.sbarLyricYSeek,
            binding.sbarLyricWidthSeek,
            binding.sbarLyricFontSizeSeek,
            binding.sbarLyricAlphaSeek
        ).forEach { seekBar ->
            seekBar.setOnSeekBarChangeListener(this)
        }
    }

    private fun setupColorList() {
        val themeAccentColor = themeEngine
            .currentTheme()
            .accentColor

        val colors = DEFAULT_COLORS.copyOf().apply {
            this[0] = themeAccentColor
        }

        colorLayoutManager = LinearLayoutManager(
            this,
            RecyclerView.HORIZONTAL,
            false
        )

        colorAdapter = ColorAdapter(colors.toList()) { color, position ->
            lifecycleScope.launch {
                statusBarLyricPreferenceStore.setTextColor(color)
            }

            binding.sbarLyricColorList.post {
                colorLayoutManager.scrollToPositionWithOffset(position, 0)
            }
        }

        binding.sbarLyricColorList.apply {
            layoutManager = colorLayoutManager
            adapter = colorAdapter
            setHasFixedSize(true)
            itemAnimator = null
            addItemDecoration(HorizontalSpaceDecoration(dp(8)))
        }
    }

    private fun observePreference() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                statusBarLyricPreferenceStore.preference.collect { preference ->
                    currentPreference = preference
                    syncUi(preference)
                }
            }
        }
    }

    private fun syncUi(preference: StatusBarLyricPreference) {
        binding.preferenceSbarLyricEnableSwitch.isChecked = preference.enabled
        binding.preferenceSbarLyricShowPausedSwitch.isChecked = preference.showPaused
        binding.preferenceSbarLyricClickableSwitch.isChecked = preference.clickable

        binding.preferenceSbarLyricContentTips.setText(
            if (preference.contentType == StatusBarLyricPreferenceStore.CONTENT_TYPE_LYRIC) {
                R.string.sbar_lyric_content_lyric
            } else {
                R.string.sbar_lyric_content_title
            }
        )

        binding.preferenceSbarLyricGravityTips.setText(
            if (preference.gravity == Gravity.CENTER) {
                R.string.sbar_lyric_gravity_center
            } else {
                R.string.sbar_lyric_gravity_left
            }
        )

        if (trackingSeekBar == null) {
            syncSeekBars(preference)
        }

        colorAdapter.selectedColor = preference.textColor
        setStatusBarLyricControlsEnabled(preference.enabled)
    }

    private fun syncSeekBars(preference: StatusBarLyricPreference) {
        suppressSeekBarCallback = true
        try {
            setSeekProgress(binding.sbarLyricXSeek, preference.xRatio)
            setSeekProgress(binding.sbarLyricYSeek, preference.yRatio)
            setSeekProgress(binding.sbarLyricWidthSeek, preference.widthRatio)
            setSeekProgress(binding.sbarLyricFontSizeSeek, preference.fontSizeRatio)
            setSeekProgress(binding.sbarLyricAlphaSeek, preference.alphaRatio)
        } finally {
            suppressSeekBarCallback = false
        }
    }

    private fun setSeekProgress(seekBar: COUISeekBar, ratio: Float) {
        seekBar.progress = (ratio.coerceIn(0f, 1f) * seekBar.max).toInt()
    }

    private fun scheduleSeekBarPersist(seekBar: COUISeekBar, ratio: Float) {
        seekBarPersistJob?.cancel()
        seekBarPersistJob = lifecycleScope.launch {
            delay(SEEK_BAR_PERSIST_DEBOUNCE_MS)
            persistSeekRatio(seekBar, ratio)
        }
    }

    private suspend fun persistSeekRatio(seekBar: COUISeekBar, ratio: Float) {
        when (seekBar) {
            binding.sbarLyricXSeek -> {
                statusBarLyricPreferenceStore.setXRatio(ratio)
            }

            binding.sbarLyricYSeek -> {
                statusBarLyricPreferenceStore.setYRatio(ratio)
            }

            binding.sbarLyricWidthSeek -> {
                statusBarLyricPreferenceStore.setWidthRatio(ratio)
            }

            binding.sbarLyricFontSizeSeek -> {
                statusBarLyricPreferenceStore.setFontSizeRatio(ratio)
            }

            binding.sbarLyricAlphaSeek -> {
                statusBarLyricPreferenceStore.setAlphaRatio(ratio)
            }
        }
    }

    private fun StatusBarLyricPreference.withSeekRatio(
        seekBar: COUISeekBar,
        ratio: Float
    ): StatusBarLyricPreference {
        val coercedRatio = ratio.coerceIn(0f, 1f)
        return when (seekBar) {
            binding.sbarLyricXSeek -> copy(xRatio = coercedRatio)
            binding.sbarLyricYSeek -> copy(yRatio = coercedRatio)
            binding.sbarLyricWidthSeek -> copy(widthRatio = coercedRatio)
            binding.sbarLyricFontSizeSeek -> copy(fontSizeRatio = coercedRatio)
            binding.sbarLyricAlphaSeek -> copy(alphaRatio = coercedRatio)
            else -> this
        }
    }

    private fun handleEnableToggle() {
        lifecycleScope.launch {
            val preference = currentPreference

            if (preference.enabled) {
                statusBarLyricPreferenceStore.disable()
                return@launch
            }

            if (!hasOverlayPermission()) {
                statusBarLyricPreferenceStore.markPendingEnableAfterPermission()
                ToastUtil.show(
                    this@StatusBarLyricsActivity,
                    R.string.float_window_permission_tip
                )
                openOverlayPermissionSettings()
                return@launch
            }

            statusBarLyricPreferenceStore.enableAfterPermissionGranted()
        }
    }

    private fun syncOverlayPermissionState() {
        lifecycleScope.launch {
            val preference = statusBarLyricPreferenceStore.getSnapshot()

            when {
                preference.enabled && !hasOverlayPermission() -> {
                    statusBarLyricPreferenceStore.disable()
                }

                preference.pendingEnableAfterPermission && hasOverlayPermission() -> {
                    statusBarLyricPreferenceStore.enableAfterPermissionGranted()
                }

                preference.pendingEnableAfterPermission && !hasOverlayPermission() -> {
                    statusBarLyricPreferenceStore.setPendingEnableAfterPermission(false)
                }
            }
        }
    }

    private fun setStatusBarLyricControlsEnabled(enabled: Boolean) {
        binding.preferenceSbarLyricContainer1.forEachChild { child ->
            if (child !== binding.preferenceSbarLyricEnable) {
                setControlEnabled(child, enabled)
            }
        }
        binding.preferenceSbarLyricContainer2.forEachChild { setControlEnabled(it, enabled) }
        binding.preferenceSbarLyricContainer3.forEachChild { setControlEnabled(it, enabled) }

        binding.toolbar.menu.findItem(R.id.menu_reset)?.isVisible = enabled
    }

    /**
     * COUI widgets draw their own disabled state, so only plain views (labels, swatches) are
     * faded — fading both would leave the COUI rows nearly invisible.
     */
    private fun setControlEnabled(view: View, enabled: Boolean) {
        when {
            view.tag == ITEM_DIVIDER_TAG -> Unit
            view is COUISwitch || view is COUISeekBar -> setEnabledRecursively(view, enabled)
            view is RecyclerView -> {
                view.alpha = if (enabled) 1f else DISABLED_ALPHA
                setEnabledRecursively(view, enabled)
            }
            view is ViewGroup -> {
                view.isEnabled = enabled
                view.forEachChild { child -> setControlEnabled(child, enabled) }
            }
            else -> {
                view.isEnabled = enabled
                view.alpha = if (enabled) 1f else DISABLED_ALPHA
            }
        }
    }

    private fun setEnabledRecursively(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is ViewGroup) {
            view.forEachChild { child -> setEnabledRecursively(child, enabled) }
        }
    }

    private fun showResetDialog() {
        val config = materialDialogConfigFactory
            .createMaterialMessageDialogConfig(this)
            .apply {
                titleText = getString(R.string.sbar_lyric_reset)
                messageText = getString(R.string.sbar_lyric_reset_msg)
                positiveButtonText = getString(R.string.confirm)
                negativeButtonText = getString(R.string.cancel)

                positiveButtonClickListener =
                    DialogInterface.OnClickListener { dialog, _ ->
                        lifecycleScope.launch {
                            statusBarLyricPreferenceStore.resetDisplayTuning()
                            dialog.dismiss()
                        }
                    }
            }

        MessageDialog.show(this, config)
    }

    private fun showContentChoiceDialog() {
        val labels = listOf(
            getString(R.string.sbar_lyric_content_title),
            getString(R.string.sbar_lyric_content_lyric)
        )

        val config = materialDialogConfigFactory
            .createMaterialListDialogConfig(this, labels)
            .apply {
                titleText = getString(R.string.sbar_lyric_content)

                selectedItemIndex =
                    if (currentPreference.contentType == StatusBarLyricPreferenceStore.CONTENT_TYPE_LYRIC) {
                        1
                    } else {
                        0
                    }

                onItemClickListener = AdapterView.OnItemClickListener { _, _, which, _ ->
                    DialogRegistry.dismissAll(this@StatusBarLyricsActivity)

                    lifecycleScope.launch {
                        statusBarLyricPreferenceStore.setContentType(
                            if (which == 1) {
                                StatusBarLyricPreferenceStore.CONTENT_TYPE_LYRIC
                            } else {
                                StatusBarLyricPreferenceStore.CONTENT_TYPE_TITLE
                            }
                        )
                    }
                }

                itemIconRes = R.drawable.vector_single_check_selector
            }

        OptionsListDialog.show(this, config)
    }

    private fun showGravityChoiceDialog() {
        val labels = listOf(
            getString(R.string.sbar_lyric_gravity_left),
            getString(R.string.sbar_lyric_gravity_center)
        )

        val config = materialDialogConfigFactory
            .createMaterialListDialogConfig(this, labels)
            .apply {
                titleText = getString(R.string.sbar_lyric_gravity)

                selectedItemIndex =
                    if (currentPreference.gravity == Gravity.CENTER) {
                        1
                    } else {
                        0
                    }

                onItemClickListener = AdapterView.OnItemClickListener { _, _, which, _ ->
                    DialogRegistry.dismissAll(this@StatusBarLyricsActivity)

                    lifecycleScope.launch {
                        statusBarLyricPreferenceStore.setGravity(
                            if (which == 0) {
                                StatusBarLyricPreferenceStore.GRAVITY_LEFT
                            } else {
                                Gravity.CENTER
                            }
                        )
                    }
                }

                itemIconRes = R.drawable.vector_single_check_selector
            }

        OptionsListDialog.show(this, config)
    }

    private fun hasOverlayPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                Settings.canDrawOverlays(this)
    }

    private fun openOverlayPermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )

        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            ToastUtil.show(this, R.string.permission_open_failed)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun ViewGroup.forEachChild(action: (View) -> Unit) {
        for (index in 0 until childCount) {
            action(getChildAt(index))
        }
    }

    private fun viewLifecycleOwnerOrActivityLaunch(
        block: suspend () -> Unit
    ) {
        lifecycleScope.launch {
            block()
        }
    }

    private inner class ColorAdapter(
        private val colors: List<Int>,
        private val onColorSelected: (Int, Int) -> Unit
    ) : RecyclerView.Adapter<ColorAdapter.ColorViewHolder>() {

        var selectedColor: Int = Color.TRANSPARENT
            set(value) {
                field = value
                notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
            }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): ColorViewHolder {
            val view = layoutInflater.inflate(
                R.layout.activity_sbar_lyric_color_item,
                parent,
                false
            )

            return ColorViewHolder(view)
        }

        override fun getItemCount(): Int {
            return colors.size
        }

        override fun onBindViewHolder(
            holder: ColorViewHolder,
            position: Int
        ) {
            holder.bind(
                color = colors[position],
                selected = colors[position] == selectedColor,
                onClick = {
                    onColorSelected(colors[position], position)
                }
            )
        }

        override fun onBindViewHolder(
            holder: ColorViewHolder,
            position: Int,
            payloads: MutableList<Any>
        ) {
            if (payloads.contains(PAYLOAD_SELECTION)) {
                holder.updateSelection(colors[position] == selectedColor)
                return
            }
            super.onBindViewHolder(holder, position, payloads)
        }

        inner class ColorViewHolder(
            itemView: View
        ) : RecyclerView.ViewHolder(itemView) {

            private val image: AppCompatImageView =
                itemView.findViewById(R.id.item_image)
            private var boundColor: Int = Color.TRANSPARENT

            fun bind(
                color: Int,
                selected: Boolean,
                onClick: () -> Unit
            ) {
                boundColor = color
                image.setImageDrawable(createColorSwatch(color))
                updateSelection(selected)
                itemView.setOnClickListener { onClick() }
            }

            fun updateSelection(selected: Boolean) {
                image.background = if (selected) {
                    createSelectedColorBackground(boundColor)
                } else {
                    null
                }
            }
        }

    }

    private fun createColorSwatch(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun createSelectedColorBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(dp(2), color)
        }
    }

    private class HorizontalSpaceDecoration(
        private val spacePx: Int
    ) : RecyclerView.ItemDecoration() {

        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            val position = parent.getChildAdapterPosition(view)

            if (position == RecyclerView.NO_POSITION) {
                return
            }

            outRect.right = spacePx
        }
    }

    companion object {
        private const val PAYLOAD_SELECTION = "selection"
        private const val SEEK_BAR_PERSIST_DEBOUNCE_MS = 120L
        private const val DISABLED_ALPHA = 0.3f
        private const val ITEM_DIVIDER_TAG = "item_divider"

        private val DEFAULT_COLORS = intArrayOf(
            -16776961,
            -16726731,
            -16617532,
            -12686337,
            -7252993,
            -1464064,
            -837120,
            -61055,
            -1900544,
            -16777216
        )

        fun start(context: Context) {
            context.startActivityCompat(
                Intent(context, StatusBarLyricsActivity::class.java)
            )
        }
    }
}
