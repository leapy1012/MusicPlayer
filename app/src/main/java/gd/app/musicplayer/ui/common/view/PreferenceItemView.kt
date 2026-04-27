package gd.app.musicplayer.ui.common.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import gd.app.musicplayer.R
import gd.app.musicplayer.core.ui.extension.dpToPx
import gd.app.musicplayer.util.PreferenceStore

class PreferenceItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs), View.OnClickListener {

    fun interface OnPreferenceChangedListener {
        fun onPreferenceChanged(view: PreferenceItemView, isEnabled: Boolean)
    }

    private var summaryWhenEnabled: String? = null
    private var summaryWhenDisabled: String? = null
    private var defaultValue: Boolean = false
    private var preferenceKey: String? = null

    private val summaryView: TextView
    private val tipsView: TextView
    private val selectBox: SelectBox
    private val titleView: TextView
    private val preferenceStore: PreferenceStore

    private var onPreferenceChangedListener: OnPreferenceChangedListener? = null
    private var externalClickListener: OnClickListener? = null

    init {
        inflate(context, R.layout.preference_list_item, this)
        setPadding(0, context.dpToPx(12f), 0, context.dpToPx(12f))

        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.PreferenceItemView)

        val titleText =
            typedArray.getString(R.styleable.PreferenceItemView_preference_item_title)
        val preferenceFileName =
            typedArray.getString(R.styleable.PreferenceItemView_preference_item_file_name)
        summaryWhenEnabled =
            typedArray.getString(R.styleable.PreferenceItemView_preference_item_summary_on)
        summaryWhenDisabled =
            typedArray.getString(R.styleable.PreferenceItemView_preference_item_summary_off)
        preferenceKey =
            typedArray.getString(R.styleable.PreferenceItemView_preference_item_key)
        defaultValue =
            typedArray.getBoolean(R.styleable.PreferenceItemView_preference_item_default, false)

        val iconResId = typedArray.getResourceId(
            R.styleable.PreferenceItemView_preference_item_check_drawable,
            View.NO_ID
        )
        val indicatorDrawable = iconResId
            .takeIf { it != View.NO_ID }
            ?.let { AppCompatResources.getDrawable(context, it) }

        typedArray.recycle()

        preferenceStore = PreferenceStore(
            context.getSharedPreferences(
                preferenceFileName ?: DEFAULT_PREFERENCE_FILE,
                Context.MODE_PRIVATE
            )
        )

        titleView = findViewById(R.id.title)
        summaryView = findViewById(R.id.summary)
        tipsView = findViewById(R.id.tips)
        selectBox = findViewById<SelectBox>(R.id.checkbox).apply {
            setOnClickListener(this@PreferenceItemView)
        }

        titleView.text = titleText.orEmpty()

        setupIndicator(indicatorDrawable, iconResId)
        updateSummaryVisibility()

        renderState(
            isSelected = readStoredValue(),
            notifyListener = false,
            persistValue = false
        )

        super.setOnClickListener(this)
    }

    override fun onClick(v: View) {
        externalClickListener?.onClick(this) ?: toggle()
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = true

    override fun isSelected(): Boolean = selectBox.isSelected

    override fun setSelected(selected: Boolean) {
        renderState(
            isSelected = selected,
            notifyListener = false,
            persistValue = true
        )
    }

    override fun setOnClickListener(listener: OnClickListener?) {
        externalClickListener = if (listener === this) null else listener
    }

    fun setOnPreferenceChangedListener(listener: OnPreferenceChangedListener?) {
        onPreferenceChangedListener = listener
    }

    fun setDefaultValue(value: Boolean) {
        defaultValue = value
        refreshFromPreference(persistCurrentValue = false)
    }

    fun setSummaryOn(text: String?) {
        summaryWhenEnabled = text
        updateSummaryVisibility()
        updateSummaryText(selectBox.isSelected)
    }

    fun setTips(text: String?) {
        tipsView.text = text
        tipsView.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    fun setTips(resId: Int) {
        setTips(resources.getString(resId))
    }

    fun getSelectBox(): SelectBox = selectBox

    fun getTipsView(): TextView = tipsView

    fun refreshFromPreference(persistCurrentValue: Boolean) {
        renderState(
            isSelected = readStoredValue(),
            notifyListener = false,
            persistValue = persistCurrentValue
        )
    }

    private fun toggle() {
        renderState(
            isSelected = !selectBox.isSelected,
            notifyListener = true,
            persistValue = true
        )
    }

    private fun renderState(
        isSelected: Boolean,
        notifyListener: Boolean,
        persistValue: Boolean
    ) {
        if (persistValue) {
            preferenceKey?.let { key ->
                preferenceStore.putBoolean(key, isSelected)
            }
        }

        selectBox.isSelected = isSelected
        updateSummaryText(isSelected)

        if (notifyListener) {
            onPreferenceChangedListener?.onPreferenceChanged(this, isSelected)
        }
    }

    private fun updateSummaryText(isSelected: Boolean) {
        val summaryText = if (isSelected) {
            summaryWhenEnabled ?: summaryWhenDisabled
        } else {
            summaryWhenDisabled ?: summaryWhenEnabled
        }

        summaryView.text = summaryText.orEmpty()
    }

    private fun updateSummaryVisibility() {
        summaryView.visibility = if (
            summaryWhenEnabled.isNullOrEmpty() && summaryWhenDisabled.isNullOrEmpty()
        ) {
            View.GONE
        } else {
            View.VISIBLE
        }
    }

    private fun readStoredValue(): Boolean {
        val key = preferenceKey ?: return defaultValue
        return preferenceStore.getBoolean(key, defaultValue)
    }

    private fun setupIndicator(
        indicatorDrawable: Drawable?,
        iconResId: Int
    ) {
        if (indicatorDrawable == null) {
            selectBox.visibility = View.GONE
            return
        }

        selectBox.visibility = View.VISIBLE
        selectBox.setImageDrawable(indicatorDrawable)

        val params = selectBox.layoutParams as? ConstraintLayout.LayoutParams ?: return

        when (iconResId) {
            R.drawable.vector_toggle_selector -> {
                (params as ViewGroup.MarginLayoutParams).width = context.dpToPx(56f)
                selectBox.scaleType = ImageView.ScaleType.CENTER_INSIDE
            }

            R.drawable.vector_arrow_right -> {
                (params as ViewGroup.MarginLayoutParams).width = context.dpToPx(24f)
                params.marginEnd = context.dpToPx(8f)
                selectBox.setPadding(0, 0, 0, 0)
                selectBox.scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
        }

        selectBox.layoutParams = params
    }

    private companion object {
        const val DEFAULT_PREFERENCE_FILE = "music_preference"
    }
}