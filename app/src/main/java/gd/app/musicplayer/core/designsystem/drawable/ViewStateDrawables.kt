package gd.app.musicplayer.core.designsystem.drawable

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toDrawable

private val STATE_DEFAULT = intArrayOf()
private val STATE_PRESSED_ENABLED = intArrayOf(
    android.R.attr.state_pressed,
    android.R.attr.state_enabled,
)
private val STATE_SELECTED_ENABLED = intArrayOf(
    android.R.attr.state_selected,
    android.R.attr.state_enabled,
)
private val STATE_FOCUSED_ENABLED = intArrayOf(
    android.R.attr.state_focused,
    android.R.attr.state_enabled,
)
private val STATE_DISABLED = intArrayOf(-android.R.attr.state_enabled)
private val STATE_ENABLED = intArrayOf(android.R.attr.state_enabled)

fun enabledDisabledColors(
    @ColorInt enabledColor: Int,
    @ColorInt disabledColor: Int,
): ColorStateList {
    return ColorStateList(
        arrayOf(STATE_DISABLED, STATE_ENABLED),
        intArrayOf(disabledColor, enabledColor),
    )
}

fun focusedDefaultColors(
    @ColorInt defaultColor: Int,
    @ColorInt focusedColor: Int,
): ColorStateList {
    return ColorStateList(
        arrayOf(STATE_FOCUSED_ENABLED, STATE_DEFAULT),
        intArrayOf(focusedColor, defaultColor),
    )
}

fun pressedDefaultColors(
    @ColorInt defaultColor: Int,
    @ColorInt pressedColor: Int,
): ColorStateList {
    return ColorStateList(
        arrayOf(STATE_PRESSED_ENABLED, STATE_DEFAULT),
        intArrayOf(pressedColor, defaultColor),
    )
}

fun selectedDefaultColors(
    @ColorInt defaultColor: Int,
    @ColorInt selectedColor: Int,
): ColorStateList {
    return ColorStateList(
        arrayOf(STATE_SELECTED_ENABLED, STATE_DEFAULT),
        intArrayOf(selectedColor, defaultColor),
    )
}

fun disabledSelectedDefaultColors(
    @ColorInt defaultColor: Int,
    @ColorInt selectedColor: Int,
    @ColorInt disabledColor: Int,
): ColorStateList {
    return ColorStateList(
        arrayOf(STATE_DISABLED, STATE_SELECTED_ENABLED, STATE_DEFAULT),
        intArrayOf(disabledColor, selectedColor, defaultColor),
    )
}

fun defaultWithDisabledDrawable(
    defaultDrawable: Drawable,
    disabledDrawable: Drawable,
): Drawable {
    return stateListDrawable {
        addState(STATE_DISABLED, disabledDrawable)
        addState(STATE_DEFAULT, defaultDrawable)
    }
}

fun pressedDefaultColorDrawable(
    @ColorInt defaultColor: Int,
    @ColorInt pressedColor: Int,
): Drawable {
    return stateListDrawable {
        addState(STATE_PRESSED_ENABLED, pressedColor.toDrawable())
        addState(STATE_DEFAULT, defaultColor.toDrawable())
    }
}

fun selectedDefaultDrawableFromRes(
    context: Context,
    @DrawableRes defaultDrawableRes: Int,
    @DrawableRes selectedDrawableRes: Int,
): Drawable {
    val selectedDrawable = context.requireDrawable(selectedDrawableRes)
    val defaultDrawable = context.requireDrawable(defaultDrawableRes)

    return stateListDrawable {
        addState(STATE_SELECTED_ENABLED, selectedDrawable)
        addState(STATE_DEFAULT, defaultDrawable)
    }
}

fun selectedDefaultDrawableFromRes(
    context: Context,
    drawableResPair: IntArray,
): Drawable {
    require(drawableResPair.size >= REQUIRED_DRAWABLE_PAIR_SIZE) {
        "drawableResPair must contain [defaultDrawableRes, selectedDrawableRes]."
    }

    return selectedDefaultDrawableFromRes(
        context = context,
        defaultDrawableRes = drawableResPair[DEFAULT_DRAWABLE_INDEX],
        selectedDrawableRes = drawableResPair[SELECTED_DRAWABLE_INDEX],
    )
}

fun stateDrawable(
    defaultDrawable: Drawable?,
    selectedDrawable: Drawable? = null,
    disabledDrawable: Drawable? = null,
): Drawable {
    return stateListDrawable {
        disabledDrawable?.let { addState(STATE_DISABLED, it) }
        selectedDrawable?.let { addState(STATE_SELECTED_ENABLED, it) }
        defaultDrawable?.let { addState(STATE_DEFAULT, it) }
    }
}

private fun stateListDrawable(
    builder: StateListDrawable.() -> Unit,
): StateListDrawable {
    return StateListDrawable().apply {
        builder()
        state = STATE_DEFAULT
    }
}

private fun Context.requireDrawable(
    @DrawableRes drawableRes: Int,
): Drawable {
    return requireNotNull(AppCompatResources.getDrawable(this, drawableRes)) {
        "Drawable resource $drawableRes could not be loaded."
    }
}

private const val REQUIRED_DRAWABLE_PAIR_SIZE = 2
private const val DEFAULT_DRAWABLE_INDEX = 0
private const val SELECTED_DRAWABLE_INDEX = 1