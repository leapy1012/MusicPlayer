package gd.app.musicplayer.core.ui.view

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import gd.app.musicplayer.R

class SearchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr),
    View.OnClickListener,
    TextWatcher,
    TextView.OnEditorActionListener {

    interface OnQueryTextListener {
        fun onQueryTextChange(query: String): Boolean
        fun onQueryTextSubmit(query: String): Boolean
    }

    private val clearButton: ImageView
    private val searchInput: EditText
    private var queryTextListener: OnQueryTextListener? = null

    init {
        LayoutInflater.from(context).inflate(
            R.layout.layout_search_view_detail,
            this as ViewGroup,
            true
        )

        clearButton = findViewById(R.id.search_close_btn)
        searchInput = findViewById(R.id.search_src_text)

//        s.b(searchInput, 120)

        clearButton.setOnClickListener(this)
        searchInput.addTextChangedListener(this)
        searchInput.setOnEditorActionListener(this)

//        applyTheme(m4.f.i().j())
    }

    fun getEditText(): EditText = searchInput

    fun setOnQueryTextListener(listener: OnQueryTextListener?) {
        queryTextListener = listener
    }

    override fun onClick(view: View) {
        if (view === clearButton) {
            searchInput.setText("")
        }
    }

    override fun onEditorAction(textView: TextView, actionId: Int, event: KeyEvent?): Boolean {
        if (actionId != 3 && actionId != 6) {
            return false
        }

        notifyQuerySubmitted()
        return false
    }

    override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
        val query = text?.toString().orEmpty()
        queryTextListener?.onQueryTextChange(query)

        clearButton.visibility = if (query.isNotEmpty()) {
            VISIBLE
        } else {
            INVISIBLE
        }
    }

    override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit

    override fun afterTextChanged(editable: Editable?) = Unit

    private fun notifyQuerySubmitted() {
        queryTextListener?.onQueryTextSubmit(searchInput.text.toString())
    }

//    private fun applyTheme(theme: m4.d) {
//        val textColor = theme.m()
//
//        searchInput.setTextColor(textColor)
//        searchInput.setHintTextColor(androidx.core.graphics.d.q(textColor, 128))
//        searchInput.highlightColor = androidx.core.graphics.d.q(theme.y(), 77)
//
//        val searchPlate = findViewById<View>(R.id.search_plate)
//        if (theme.q()) {
//            searchPlate.background = null
//        } else {
//            a1.o(searchPlate, androidx.core.graphics.d.q(textColor, 128))
//        }
//
//        ImageViewCompat.setImageTintList(clearButton, ColorStateList.valueOf(textColor))
//        a1.p(clearButton, r.a(0, theme.k()))
//    }
}