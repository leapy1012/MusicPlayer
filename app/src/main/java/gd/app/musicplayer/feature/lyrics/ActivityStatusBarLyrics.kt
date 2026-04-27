package gd.app.musicplayer.feature.lyrics

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.R
import gd.app.musicplayer.core.ui.extension.startActivityCompat
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.databinding.ActivityStatusBarLyricsBinding
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import gd.app.musicplayer.ui.common.dialog.ChoiceListDialog
import gd.app.musicplayer.ui.common.dialog.DialogRegistry
import gd.app.musicplayer.ui.common.dialog.MaterialDialogConfig
import gd.app.musicplayer.ui.common.dialog.MessageDialog
import gd.app.musicplayer.ui.common.view.SeekBar
import gd.app.musicplayer.util.PreferenceUtil
import gd.app.musicplayer.util.StatusBarLyricSettings

class ActivityStatusBarLyrics : BaseActivity(), SeekBar.OnSeekBarChangeListener {

    private lateinit var binding: ActivityStatusBarLyricsBinding
    private lateinit var settings: StatusBarLyricSettings
    private lateinit var colorAdapter: ColorAdapter
    private lateinit var colorLayoutManager: LinearLayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatusBarLyricsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = StatusBarLyricSettings.from(this)

        setupToolbar()
        setupPreferences()
        setupSeekBars()
        setupColorList()
        syncUi()
    }

    override fun onResume() {
        super.onResume()

        if (settings.enabled && !hasOverlayPermission()) {
            settings.enabled = false
        }
        if (settings.pendingEnableAfterPermission && hasOverlayPermission()) {
            settings.pendingEnableAfterPermission = false
            settings.enabled = true
        } else if (settings.pendingEnableAfterPermission && !hasOverlayPermission()) {
            settings.pendingEnableAfterPermission = false
        }
        syncUi()
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (!fromUser) return

        val max = seekBar.getMax().coerceAtLeast(1)
        val ratio = progress.toFloat() / max.toFloat()
        when (seekBar) {
            binding.sbarLyricXSeek -> settings.xRatio = ratio
            binding.sbarLyricYSeek -> settings.setYRatio(ratio)
            binding.sbarLyricWidthSeek -> settings.widthRatio = ratio
            binding.sbarLyricFontSizeSeek -> settings.fontSizeRatio = ratio
            binding.sbarLyricAlphaSeek -> settings.alphaRatio = ratio
        }
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        binding.settingScrollView.requestDisallowInterceptTouchEvent(false)
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        binding.settingScrollView.requestDisallowInterceptTouchEvent(true)
    }

    private fun setupToolbar() {
        setupEdgeToEdgeToolbar(
            root = binding.root,
            statusBarView = binding.statusBarSpace,
            bottomPaddingView = binding.root,
            toolbar = binding.toolbar,
            titleRes = R.string.sbar_lyric
        )
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.toolbar.setOnMenuItemClickListener { item: MenuItem ->
            if (item.itemId == R.id.menu_reset) {
                showResetDialog()
            }
            true
        }
    }

    private fun setupPreferences() {
        binding.preferenceSbarLyricEnable.setOnClickListener {
            handleEnableToggle()
        }
        binding.preferenceSbarLyricShowPaused.setOnClickListener {
            val next = !settings.showPaused
            settings.showPaused = next
            binding.preferenceSbarLyricShowPaused.setSelected(next)
        }
        binding.preferenceSbarLyricClickable.setOnClickListener {
            val next = !settings.clickable
            settings.clickable = next
            binding.preferenceSbarLyricClickable.setSelected(next)
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
        ).forEach { it.setOnSeekBarChangeListener(this) }
    }

    private fun setupColorList() {
        val colors = DEFAULT_COLORS.copyOf().apply {
            this[0] = PreferenceUtil.getInstance(this@ActivityStatusBarLyrics).getThemeColor()
        }
        colorLayoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        colorAdapter = ColorAdapter(colors.toList()) { color, position ->
            settings.textColor = color
            colorAdapter.selectedColor = color
            binding.sbarLyricColorList.post {
                colorLayoutManager.scrollToPositionWithOffset(position, 0)
            }
        }
        binding.sbarLyricColorList.layoutManager = colorLayoutManager
        binding.sbarLyricColorList.adapter = colorAdapter
        binding.sbarLyricColorList.setHasFixedSize(true)
        binding.sbarLyricColorList.itemAnimator = null
        binding.sbarLyricColorList.addItemDecoration(HorizontalSpaceDecoration(dp(8)))
    }

    private fun syncUi() {
        val enabled = settings.enabled
        binding.preferenceSbarLyricEnable.setSelected(enabled)
        binding.preferenceSbarLyricShowPaused.setSelected(settings.showPaused)
        binding.preferenceSbarLyricClickable.setSelected(settings.clickable)
        binding.preferenceSbarLyricContent.setTips(
            if (settings.contentType == StatusBarLyricSettings.CONTENT_TYPE_LYRIC) {
                R.string.sbar_lyric_content_lyric
            } else {
                R.string.sbar_lyric_content_title
            }
        )
        binding.preferenceSbarLyricGravity.setTips(
            if (settings.gravity == Gravity.CENTER) {
                R.string.sbar_lyric_gravity_center
            } else {
                R.string.sbar_lyric_gravity_left
            }
        )

        binding.sbarLyricXSeek.setProgress((settings.xRatio * binding.sbarLyricXSeek.getMax()).toInt())
        binding.sbarLyricYSeek.setProgress((settings.yRatio() * binding.sbarLyricYSeek.getMax()).toInt())
        binding.sbarLyricWidthSeek.setProgress((settings.widthRatio * binding.sbarLyricWidthSeek.getMax()).toInt())
        binding.sbarLyricFontSizeSeek.setProgress((settings.fontSizeRatio * binding.sbarLyricFontSizeSeek.getMax()).toInt())
        binding.sbarLyricAlphaSeek.setProgress((settings.alphaRatio * binding.sbarLyricAlphaSeek.getMax()).toInt())

        colorAdapter.selectedColor = settings.textColor
        setStatusBarLyricControlsEnabled(enabled)
    }

    private fun handleEnableToggle() {
        if (settings.enabled) {
            settings.enabled = false
            settings.pendingEnableAfterPermission = false
            syncUi()
            return
        }

        if (!hasOverlayPermission()) {
            settings.pendingEnableAfterPermission = true
            ToastUtil.show(this, R.string.float_window_permission_tip)
            openOverlayPermissionSettings()
            return
        }

        settings.enabled = true
        settings.pendingEnableAfterPermission = false
        syncUi()
    }

    private fun setStatusBarLyricControlsEnabled(enabled: Boolean) {
        binding.preferenceSbarLyricContainer1.forEachChild { child ->
            if (child !== binding.preferenceSbarLyricEnable) {
                setControlEnabled(child, enabled)
            }
        }
        setControlEnabled(binding.preferenceSbarLyricContainer2, enabled)
        setControlEnabled(binding.preferenceSbarLyricContainer3, enabled)
        binding.toolbar.menu.findItem(R.id.menu_reset)?.isVisible = enabled
    }

    private fun setControlEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.alpha = if (enabled) 1f else 0.45f
        if (view is ViewGroup) {
            view.forEachChild { child -> setControlEnabled(child, enabled) }
        }
    }

    private fun showResetDialog() {
        val config = MaterialDialogConfig.createMaterialMessageDialogConfig(this).apply {
            titleText = getString(R.string.sbar_lyric_reset)
            messageText = getString(R.string.sbar_lyric_reset_msg)
            positiveButtonText = getString(R.string.confirm)
            negativeButtonText = getString(R.string.cancel)
            positiveButtonClickListener =
                DialogInterface.OnClickListener { dialog, _ ->
                    settings.resetDisplayTuning()
                    syncUi()
                    dialog.dismiss()
                }
        }
        MessageDialog.show(this, config)
    }

    private fun showContentChoiceDialog() {
        val labels = listOf(
            getString(R.string.sbar_lyric_content_title),
            getString(R.string.sbar_lyric_content_lyric)
        )
        val config = MaterialDialogConfig.createMaterialListDialogConfig(this, labels).apply {
            titleText = getString(R.string.sbar_lyric_content)
            selectedItemIndex =
                if (settings.contentType == StatusBarLyricSettings.CONTENT_TYPE_LYRIC) 1 else 0
            onItemClickListener = AdapterView.OnItemClickListener { _, _, which, _ ->
                DialogRegistry.dismissAll(this@ActivityStatusBarLyrics)
                settings.contentType =
                    if (which == 1) StatusBarLyricSettings.CONTENT_TYPE_LYRIC
                    else StatusBarLyricSettings.CONTENT_TYPE_TITLE
                syncUi()
            }
            itemIconRes = R.drawable.vector_single_check_selector
        }
        ChoiceListDialog.show(this, config)
    }

    private fun showGravityChoiceDialog() {
        val labels = listOf(
            getString(R.string.sbar_lyric_gravity_left),
            getString(R.string.sbar_lyric_gravity_center)
        )
        val config = MaterialDialogConfig.createMaterialListDialogConfig(this, labels).apply {
            titleText = getString(R.string.sbar_lyric_gravity)
            selectedItemIndex = if (settings.gravity == Gravity.CENTER) 1 else 0
            onItemClickListener = AdapterView.OnItemClickListener { _, _, which, _ ->
                DialogRegistry.dismissAll(this@ActivityStatusBarLyrics)
                settings.gravity =
                    if (which == 0) StatusBarLyricSettings.GRAVITY_LEFT else Gravity.CENTER
                syncUi()
            }
            itemIconRes = R.drawable.vector_single_check_selector
        }
        ChoiceListDialog.show(this, config)
    }

    private fun hasOverlayPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun ViewGroup.forEachChild(action: (View) -> Unit) {
        for (index in 0 until childCount) {
            action(getChildAt(index))
        }
    }

    private inner class ColorAdapter(
        private val colors: List<Int>,
        private val onColorSelected: (Int, Int) -> Unit
    ) : RecyclerView.Adapter<ColorAdapter.ColorViewHolder>() {

        var selectedColor: Int = Color.TRANSPARENT
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
            val view = layoutInflater.inflate(
                R.layout.activity_sbar_lyric_color_item,
                parent,
                false
            )
            return ColorViewHolder(view)
        }

        override fun getItemCount(): Int = colors.size

        override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
            val color = colors[position]
            holder.bind(color, color == selectedColor) {
                onColorSelected(color, position)
            }
        }

        inner class ColorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val image = itemView.findViewById<AppCompatImageView>(
                R.id.item_image
            )

            fun bind(color: Int, selected: Boolean, onClick: () -> Unit) {
                image.setImageDrawable(createColorDrawable(color, selected))
                image.setOnClickListener { onClick() }
            }
        }
    }

    private fun createColorDrawable(color: Int, selected: Boolean): Drawable {
        val circle = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(1), ColorUtils.setAlphaComponent(Color.WHITE, 90))
        }
        if (!selected) return circle

        val check = AppCompatResources.getDrawable(this, R.drawable.vector_single_check_selector)
            ?.mutate()
            ?.let { DrawableCompat.wrap(it) }
        check?.setTint(Color.WHITE)
        return LayerDrawable(
            arrayOf(
                circle,
                check ?: circle
            )
        )
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
            if (position == RecyclerView.NO_POSITION) return
            outRect.right = spacePx
            if (position == 0) {
                outRect.left = spacePx
            }
        }
    }

    companion object {
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
            context.startActivityCompat(Intent(context, ActivityStatusBarLyrics::class.java))
        }
    }
}
