package gd.app.musicplayer.core.ui.drawable

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toDrawable

object ViewStateDrawables {

    private val STATE_DEFAULT = intArrayOf()
    private val STATE_PRESSED_ENABLED = intArrayOf(
        android.R.attr.state_pressed,
        android.R.attr.state_enabled
    )
    private val STATE_SELECTED_ENABLED = intArrayOf(
        android.R.attr.state_selected,
        android.R.attr.state_enabled
    )
    private val STATE_CHECKED_ENABLED = intArrayOf(
        android.R.attr.state_checked,
        android.R.attr.state_enabled
    )
    private val STATE_UNCHECKED = intArrayOf(-android.R.attr.state_checked)
    private val STATE_FOCUSED_ENABLED = intArrayOf(
        android.R.attr.state_focused,
        android.R.attr.state_enabled
    )
    private val STATE_DISABLED = intArrayOf(-android.R.attr.state_enabled)
    private val STATE_ENABLED = intArrayOf(android.R.attr.state_enabled)

    fun enabledDisabledColors(
        enabledColor: Int,
        disabledColor: Int
    ): ColorStateList {
        return ColorStateList(
            arrayOf(STATE_DISABLED, STATE_ENABLED),
            intArrayOf(disabledColor, enabledColor)
        )
    }

    fun focusedDefaultColors(
        defaultColor: Int,
        focusedColor: Int
    ): ColorStateList {
        return ColorStateList(
            arrayOf(STATE_FOCUSED_ENABLED, STATE_ENABLED),
            intArrayOf(focusedColor, defaultColor)
        )
    }

    fun pressedDefaultColors(
        defaultColor: Int,
        pressedColor: Int
    ): ColorStateList {
        return ColorStateList(
            arrayOf(STATE_PRESSED_ENABLED, STATE_DEFAULT),
            intArrayOf(pressedColor, defaultColor)
        )
    }

    fun selectedDefaultColors(
        defaultColor: Int,
        selectedColor: Int
    ): ColorStateList {
        return ColorStateList(
            arrayOf(STATE_SELECTED_ENABLED, STATE_DEFAULT),
            intArrayOf(selectedColor, defaultColor)
        )
    }

    fun disabledSelectedDefaultColors(
        defaultColor: Int,
        selectedColor: Int,
        disabledColor: Int
    ): ColorStateList {
        return ColorStateList(
            arrayOf(STATE_DISABLED, STATE_SELECTED_ENABLED, STATE_ENABLED),
            intArrayOf(disabledColor, selectedColor, defaultColor)
        )
    }

    fun defaultWithDisabledDrawable(
        defaultDrawable: Drawable,
        disabledDrawable: Drawable
    ): Drawable {
        return StateListDrawable().apply {
            addState(STATE_DISABLED, disabledDrawable)
            addState(STATE_DEFAULT, defaultDrawable)
            state = STATE_DEFAULT
        }
    }

    fun pressedDefaultColorDrawable(
        defaultColor: Int,
        pressedColor: Int
    ): Drawable {
        return StateListDrawable().apply {
            addState(STATE_PRESSED_ENABLED, pressedColor.toDrawable())
            addState(STATE_DEFAULT, defaultColor.toDrawable())
            state = STATE_DEFAULT
        }
    }

    fun selectedDefaultDrawableFromRes(
        context: Context,
        drawableResPair: IntArray
    ): Drawable {
        require(drawableResPair.size >= 2) {
            "drawableResPair must have [defaultRes, selectedRes]"
        }

        val selected = requireNotNull(AppCompatResources.getDrawable(context, drawableResPair[1]))
        val default = requireNotNull(AppCompatResources.getDrawable(context, drawableResPair[0]))

        return StateListDrawable().apply {
            addState(STATE_SELECTED_ENABLED, selected)
            addState(STATE_DEFAULT, default)
            state = STATE_DEFAULT
        }
    }

    fun buildStateDrawable(
        defaultDrawable: Drawable?,
        selectedDrawable: Drawable?,
        disabledDrawable: Drawable?
    ): Drawable {
        return StateListDrawable().apply {
            if (disabledDrawable != null) addState(STATE_DISABLED, disabledDrawable)
            if (selectedDrawable != null) addState(STATE_SELECTED_ENABLED, selectedDrawable)
            addState(STATE_DEFAULT, defaultDrawable)
            state = STATE_DEFAULT
        }
    }
}