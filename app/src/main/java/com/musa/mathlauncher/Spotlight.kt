package com.musa.mathlauncher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class SpotlightPanel(
    context: Context,
    private val search: (String) -> List<AppEntry>,
    private val iconFactory: (AppEntry) -> ImageView,
    private val onPick: (AppEntry) -> Unit
) : FrameLayout(context) {

    private val input = EditText(context)
    private val list = LinearLayout(context)
    private val column = LinearLayout(context)
    private var topInset = 0
    private var bottomInset = 0

    private fun px(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    init {
        visibility = View.GONE
        isClickable = true
        setBackgroundColor(0xF2101022.toInt())

        column.orientation = LinearLayout.VERTICAL
        applyPadding()

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        input.hint = "Search"
        input.setHintTextColor(0x99FFFFFF.toInt())
        input.setTextColor(Color.WHITE)
        input.textSize = 16f
        input.maxLines = 1
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.imeOptions = EditorInfo.IME_ACTION_SEARCH
        input.setPadding(px(14), 0, px(14), 0)
        input.compoundDrawablePadding = px(8)
        val mag = context.getDrawable(android.R.drawable.ic_menu_search)?.mutate()
        if (mag != null) {
            mag.setTint(Color.WHITE)
            mag.setBounds(0, 0, px(18), px(18))
            input.setCompoundDrawables(mag, null, null, null)
        }
        input.background = GradientDrawable().apply {
            cornerRadius = px(22).toFloat()
            setColor(0x33FFFFFF)
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                render()
            }
        })
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val first = search(input.text.toString().trim()).firstOrNull()
                if (first != null) onPick(first)
                true
            } else {
                false
            }
        }
        topRow.addView(input, LinearLayout.LayoutParams(0, px(44), 1f))

        val cancel = TextView(context).apply {
            text = "Cancel"
            textSize = 16f
            setTextColor(0xFFFF9F0A.toInt())
            setPadding(px(14), px(10), 0, px(10))
            setOnClickListener { hide() }
        }
        topRow.addView(cancel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        column.addView(topRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        list.orientation = LinearLayout.VERTICAL
        val scroll = ScrollView(context)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(list, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        column.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply { topMargin = px(12) })

        addView(column, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    private fun applyPadding() {
        column.setPadding(px(16), topInset + px(12), px(16), bottomInset)
    }

    fun setInsets(top: Int, bottom: Int) {
        topInset = top
        bottomInset = bottom
        applyPadding()
    }

    private fun render() {
        list.removeAllViews()
        val q = input.text.toString().trim()
        val results = search(q)

        val header = TextView(context).apply {
            text = if (q.isEmpty()) "Suggestions" else "Apps"
            textSize = 13f
            setTextColor(0xAAFFFFFF.toInt())
            typeface = Typeface.DEFAULT_BOLD
            setPadding(px(4), px(8), 0, px(6))
        }
        list.addView(header)

        if (results.isEmpty()) {
            list.addView(TextView(context).apply {
                text = "No results"
                textSize = 15f
                setTextColor(0x88FFFFFF.toInt())
                setPadding(px(4), px(12), 0, 0)
            })
            return
        }

        for (a in results) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(px(4), px(8), px(4), px(8))
                setOnClickListener { onPick(a) }
            }
            row.addView(iconFactory(a), LinearLayout.LayoutParams(px(46), px(46)))
            row.addView(TextView(context).apply {
                text = a.label
                textSize = 17f
                setTextColor(Color.WHITE)
                maxLines = 1
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = px(14) })
            list.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    fun show() {
        input.setText("")
        render()
        alpha = 0f
        visibility = View.VISIBLE
        animate().alpha(1f).setDuration(180).start()
        input.requestFocus()
        input.post {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun hide() {
        if (visibility != View.VISIBLE) return
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
        input.clearFocus()
        animate().alpha(0f).setDuration(150).withEndAction {
            visibility = View.GONE
        }.start()
    }
}
