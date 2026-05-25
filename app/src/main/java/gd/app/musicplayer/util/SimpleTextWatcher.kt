package gd.app.musicplayer.util

import android.text.Editable
import android.text.TextWatcher

class SimpleTextWatcher(
    private val onAfterTextChanged: (String) -> Unit
) : TextWatcher {

    override fun afterTextChanged(editable: Editable?) {
        onAfterTextChanged(editable?.toString().orEmpty())
    }

    override fun beforeTextChanged(
        text: CharSequence?,
        start: Int,
        count: Int,
        after: Int
    ) = Unit

    override fun onTextChanged(
        text: CharSequence?,
        start: Int,
        before: Int,
        count: Int
    ) = Unit
}