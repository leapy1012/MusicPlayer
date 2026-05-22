package gd.app.musicplayer.ui.widget

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.dpToPx
import gd.app.musicplayer.core.designsystem.drawable.DrawableUtil
import gd.app.musicplayer.core.designsystem.theme.ThemePalette
import gd.app.musicplayer.core.designsystem.theme.accentColor
import gd.app.musicplayer.core.designsystem.theme.contentSurfaceLight
import gd.app.musicplayer.core.designsystem.theme.rippleColor
import gd.app.musicplayer.databinding.ActivityWidgetItemBinding
import androidx.core.graphics.withClip

internal class WidgetSizeAdapter(
    private val items: List<WidgetProviderSpec>,
    private val applyTheme: (View) -> Unit,
    private val currentTheme: () -> ThemePalette,
    private val onAddClicked: (WidgetProviderSpec) -> Unit
) : RecyclerView.Adapter<WidgetSizeAdapter.WidgetSizeViewHolder>() {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): WidgetSizeViewHolder {
        val binding = ActivityWidgetItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        applyTheme(binding.root)
        binding.applyReferenceItemTheme(currentTheme())

        return WidgetSizeViewHolder(
            binding = binding,
            currentTheme = currentTheme,
            onAddClicked = { position ->
                items.getOrNull(position)?.let(onAddClicked)
            }
        )
    }

    override fun onBindViewHolder(
        holder: WidgetSizeViewHolder,
        position: Int
    ) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    internal class WidgetSizeViewHolder(
        private val binding: ActivityWidgetItemBinding,
        private val currentTheme: () -> ThemePalette,
        private val onAddClicked: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener { notifyAddClicked() }
            binding.itemAdd.setOnClickListener { notifyAddClicked() }
        }

        fun bind(item: WidgetProviderSpec) = with(binding) {
            applyReferenceItemTheme(currentTheme())

            itemTitle.setText(item.titleRes)

            itemSize.text = root.context.getString(
                R.string.widget_size_format,
                item.classify
            )

            itemImage.setReferencePreview(
                previewRes = item.previewRes,
                classify = item.classify
            )
        }

        private fun notifyAddClicked() {
            val position = bindingAdapterPosition
                .takeIf { it != RecyclerView.NO_POSITION }
                ?: return

            onAddClicked(position)
        }

        private fun ImageView.setReferencePreview(
            previewRes: Int,
            classify: String
        ) {
            val preview = AppCompatResources.getDrawable(context, previewRes)
                ?: return
            val maxPreviewHeight = when {
                classify.endsWith("1") -> context.dpToPx(PREVIEW_HEIGHT_SMALL_DP)
                classify.endsWith("2") -> context.dpToPx(PREVIEW_HEIGHT_MEDIUM_DP)
                else -> 0
            }

            adjustViewBounds = false
            scaleType = ImageView.ScaleType.FIT_XY
            setImageDrawable(
                WidgetPreviewDrawable(
                    drawable = preview,
                    maxHeight = maxPreviewHeight,
                    alignEnd = true,
                    isRtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
                )
            )
        }

        private companion object {
            const val PREVIEW_HEIGHT_SMALL_DP = 64f
            const val PREVIEW_HEIGHT_MEDIUM_DP = 80f
        }
    }
}

private fun ActivityWidgetItemBinding.applyReferenceItemTheme(theme: ThemePalette) {
    val context = root.context
    val fillColor = if (theme.contentSurfaceLight) {
        0x0D000000
    } else {
        0x0DFFFFFF
    }

    root.background = DrawableUtil.roundedRipple(
        fillColor = fillColor,
        rippleColor = theme.rippleColor,
        radius = context.dpToPx(12f).toFloat()
    )

    itemAdd.applyReferenceAddTheme(theme)
}

private fun TextView.applyReferenceAddTheme(theme: ThemePalette) {
    val context = this.context
    setTextColor(theme.accentColor)
    background = DrawableUtil.outlinedRoundedRipple(
        cornerRadius = context.dpToPx(50f),
        strokeWidth = context.dpToPx(1.5f).coerceAtLeast(1),
        strokeColor = theme.accentColor,
        fillColor = 0x33FFFFFF,
        rippleColor = ColorUtils.setAlphaComponent(theme.accentColor, 38)
    )
}

private class WidgetPreviewDrawable(
    private val drawable: Drawable,
    private val maxHeight: Int,
    private val alignEnd: Boolean,
    private val isRtl: Boolean
) : Drawable() {

    override fun draw(canvas: Canvas) {
        canvas.withClip(bounds) {
            drawable.draw(this)
        }
    }

    override fun onBoundsChange(bounds: Rect) {
        val intrinsicWidth = drawable.intrinsicWidth
        val intrinsicHeight = drawable.intrinsicHeight
        val availableWidth = bounds.width()
        var availableHeight = bounds.height()
        val topOffset: Int

        if (maxHeight > 0) {
            topOffset = (availableHeight - maxHeight) / 2
            availableHeight = minOf(maxHeight, availableHeight)
        } else {
            topOffset = 0
        }

        if (
            intrinsicWidth <= 0 ||
            intrinsicHeight <= 0 ||
            availableWidth <= 0 ||
            availableHeight <= 0
        ) {
            drawable.bounds = bounds
            return
        }

        val scale = maxOf(
            intrinsicWidth.toFloat() / availableWidth.toFloat(),
            intrinsicHeight.toFloat() / availableHeight.toFloat()
        )
        val scaledWidth = (intrinsicWidth.toFloat() / scale + 0.5f).toInt()
        val scaledHeight = (intrinsicHeight.toFloat() / scale + 0.5f).toInt()
        val top = bounds.top + topOffset + (availableHeight - scaledHeight) / 2
        val left = if (alignEnd xor isRtl) {
            bounds.left + availableWidth - scaledWidth
        } else {
            bounds.left
        }

        drawable.bounds = Rect(
            left,
            top,
            left + scaledWidth,
            top + scaledHeight
        )
    }

    override fun setAlpha(alpha: Int) {
        drawable.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        drawable.colorFilter = colorFilter
    }

    override fun getColorFilter(): ColorFilter? = drawable.colorFilter

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun setTint(tintColor: Int) {
        drawable.setTint(tintColor)
    }

    override fun setTintList(tint: ColorStateList?) {
        drawable.setTintList(tint)
    }
}
