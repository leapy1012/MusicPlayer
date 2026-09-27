package gd.app.musicplayer.core.designsystem.view

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.coui.appcompat.cardlist.COUICardListHelper
import com.coui.appcompat.couiswitch.COUISwitch
import com.coui.appcompat.preference.COUICustomListSelectedLinearLayout
import gd.app.musicplayer.R
import gd.app.musicplayer.core.datastore.PreferenceSharedStore

/**
 * Settings row that **is** a COUI card preference row
 * ([COUICustomListSelectedLinearLayout] + coui_preference children + switch/jump widgets).
 *
 * Checked state uses [Checkable] ([isChecked]/[setChecked]) — do not use View selected.
 */
class PreferenceItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : COUICustomListSelectedLinearLayout(context, attrs), View.OnClickListener {

    fun interface OnPreferenceChangedListener {
        fun onPreferenceChanged(view: PreferenceItemView, isEnabled: Boolean)
    }

    private var summaryWhenEnabled: String? = null
    private var summaryWhenDisabled: String? = null
    private var defaultValue: Boolean = false
    private var preferenceKey: String? = null
    private var preferenceStore: PreferenceSharedStore? = null
    private var useCouiSwitch: Boolean = false

    private val titleView: TextView
    private val summaryView: TextView
    private val assignmentView: TextView
    private val widgetFrame: LinearLayout
    private var couiSwitch: COUISwitch? = null

    private var onPreferenceChangedListener: OnPreferenceChangedListener? = null
    private var externalClickListener: OnClickListener? = null

    init {
        clipChildren = false
        clipToPadding = false
        adoptCouiPreferenceChrome()

        titleView = findViewById(android.R.id.title)
        summaryView = findViewById(android.R.id.summary)
        assignmentView = findViewById(com.coui.appcompat.R.id.assignment)
        widgetFrame = findViewById(android.R.id.widget_frame)

        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.PreferenceItemView)
        val titleText =
            typedArray.getString(R.styleable.PreferenceItemView_preference_item_title)
        summaryWhenEnabled =
            typedArray.getString(R.styleable.PreferenceItemView_preference_item_summary_on)
        summaryWhenDisabled =
            typedArray.getString(R.styleable.PreferenceItemView_preference_item_summary_off)
        defaultValue =
            typedArray.getBoolean(R.styleable.PreferenceItemView_preference_item_default, false)
        preferenceKey =
            typedArray.getString(R.styleable.PreferenceItemView_preference_item_key)
        val fileName =
            typedArray.getString(R.styleable.PreferenceItemView_preference_item_file_name)
        val iconResId = typedArray.getResourceId(
            R.styleable.PreferenceItemView_preference_item_check_drawable,
            NO_ID
        )
        typedArray.recycle()

        if (!fileName.isNullOrBlank()) {
            preferenceStore = PreferenceSharedStore(fileName)
        }

        titleView.text = titleText.orEmpty()
        setupWidget(iconResId)
        updateSummaryVisibility()

        val initialChecked = preferenceKey?.let { key ->
            preferenceStore?.getBoolean(context, key, defaultValue) ?: defaultValue
        } ?: defaultValue

        renderState(
            isChecked = initialChecked,
            notifyListener = false,
            persist = false
        )

        super.setOnClickListener(this)
        isClickable = true
    }

    private fun adoptCouiPreferenceChrome() {
        val source = LayoutInflater.from(context).inflate(
            com.coui.appcompat.R.layout.coui_preference,
            null,
            false
        ) as COUICustomListSelectedLinearLayout

        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = source.minimumHeight
        setPaddingRelative(
            source.paddingStart,
            source.paddingTop,
            source.paddingEnd,
            source.paddingBottom
        )
        clipChildren = source.clipChildren
        clipToPadding = source.clipToPadding

        while (source.childCount > 0) {
            val child = source.getChildAt(0)
            source.removeViewAt(0)
            addView(child)
        }
    }

    fun setCardPositionInGroup(position: Int) {
        COUICardListHelper.setItemCardBackground(this, position)
    }

    override fun onClick(v: View) {
        externalClickListener?.onClick(this) ?: run {
            if (useCouiSwitch) toggle()
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = true

    override fun isChecked(): Boolean = couiSwitch?.isChecked ?: false

    override fun setChecked(checked: Boolean) {
        renderState(
            isChecked = checked,
            notifyListener = false,
            persist = preferenceKey != null
        )
    }

    override fun toggle() {
        renderState(
            isChecked = !isChecked,
            notifyListener = true,
            persist = true
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
        updateSummaryText(isChecked)
    }

    fun setTips(text: String?) {
        assignmentView.text = text
        assignmentView.visibility = if (text.isNullOrEmpty()) GONE else VISIBLE
    }

    fun setTips(resId: Int) {
        setTips(resources.getString(resId))
    }

    fun getTipsView(): TextView = assignmentView

    fun refreshFromPreference(persistCurrentValue: Boolean) {
        val key = preferenceKey
        val checked = if (key != null) {
            preferenceStore?.getBoolean(context, key, defaultValue) ?: defaultValue
        } else {
            defaultValue
        }
        renderState(
            isChecked = checked,
            notifyListener = false,
            persist = persistCurrentValue && key != null
        )
    }

    private fun renderState(
        isChecked: Boolean,
        notifyListener: Boolean,
        persist: Boolean
    ) {
        if (persist) {
            preferenceKey?.let { key ->
                preferenceStore?.putBoolean(context, key, isChecked)
            }
        }
        couiSwitch?.isChecked = isChecked
        updateSummaryText(isChecked)
        if (notifyListener) {
            onPreferenceChangedListener?.onPreferenceChanged(this, isChecked)
        }
    }

    private fun updateSummaryText(isChecked: Boolean) {
        val summaryText = if (isChecked) {
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
            GONE
        } else {
            VISIBLE
        }
    }

    private fun setupWidget(iconResId: Int) {
        widgetFrame.removeAllViews()
        useCouiSwitch = false
        couiSwitch = null

        when (iconResId) {
            R.drawable.vector_toggle_selector -> {
                useCouiSwitch = true
                val switchView = LayoutInflater.from(context).inflate(
                    com.coui.appcompat.R.layout.coui_preference_widget_switch,
                    widgetFrame,
                    false
                ) as COUISwitch
                switchView.isClickable = false
                switchView.isFocusable = false
                widgetFrame.addView(switchView)
                couiSwitch = switchView
            }

            R.drawable.vector_arrow_right -> {
                LayoutInflater.from(context).inflate(
                    com.coui.appcompat.R.layout.coui_preference_widget_jump,
                    widgetFrame,
                    true
                )
            }

            NO_ID -> Unit

            else -> {
                LayoutInflater.from(context).inflate(
                    com.coui.appcompat.R.layout.coui_preference_widget_jump,
                    widgetFrame,
                    true
                )
            }
        }
    }
}
