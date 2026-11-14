package com.musa.mathlauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView

private const val DR_MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val DR_WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** The dock button: a dark icon with a 3x3 grid of dots */
class DrawerButtonView(
    context: Context,
    private val circle: () -> Boolean
) : View(context) {

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1F2147.toInt() }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val rect = RectF()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(0f, 0f, w, h)
        if (circle()) {
            c.drawOval(rect, bg)
        } else {
            c.drawRoundRect(rect, w * 0.225f, w * 0.225f, bg)
        }
        val gap = w / 4.2f
        val r = w * 0.065f
        val x0 = w / 2f - gap
        val y0 = h / 2f - gap
        for (i in 0..2) {
            for (j in 0..2) {
                c.drawCircle(x0 + j * gap, y0 + i * gap, r, dot)
            }
        }
    }
}

class AppDrawer(
    context: Context,
    private val appsProvider: () -> List<AppEntry>,
    private val cellFactory: (AppEntry) -> View,
    private val onLaunch: (AppEntry) -> Unit,
    private val onMenu: (AppEntry) -> Unit,
    private val onManage: () -> Unit
) : FrameLayout(context) {

    private val input = EditText(context)
    private val grid = GridView(context)
    private val column = LinearLayout(context)
    private val sidebar = LinearLayout(context)
    private var items: List<AppEntry> = emptyList()
    private var topInset = 0
    private var bottomInset = 0
    private val letters: List<String> = ('A'..'Z').map { it.toString() } + "#"

    private fun px(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    init {
        visibility = View.GONE
        isClickable = true
        setBackgroundColor(0xF2101022.toInt())

        column.orientation = LinearLayout.VERTICAL
        applyPadding()

        input.hint = "Search apps..."
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
                refresh()
            }
        })
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val first = items.firstOrNull()
                if (first != null) onLaunch(first)
                true
            } else {
                false
            }
        }
        column.addView(input, LinearLayout.LayoutParams(DR_MATCH, px(44)))

        val header = LinearLayout(context)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(px(4), px(16), px(4), px(4))
        header.addView(TextView(context).apply {
            text = "All Apps"
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, DR_WRAP, 1f))
        header.addView(TextView(context).apply {
            text = "Manage"
            textSize = 14f
            setTextColor(0xFFA78BFA.toInt())
            setPadding(px(10), px(6), px(4), px(6))
            setOnClickListener { onManage() }
        })
        column.addView(header, LinearLayout.LayoutParams(DR_MATCH, DR_WRAP))

        grid.numColumns = 4
        grid.verticalSpacing = px(18)
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.setSelector(android.R.color.transparent)
        grid.overScrollMode = View.OVER_SCROLL_NEVER
        grid.isVerticalScrollBarEnabled = false
        grid.clipToPadding = false
        grid.setPadding(px(4), px(8), px(24), px(8))
        grid.setOnItemClickListener { _, _, pos, _ ->
            onLaunch(items[pos])
        }
        grid.setOnItemLongClickListener { _, _, pos, _ ->
            onMenu(items[pos])
            true
        }

        sidebar.orientation = LinearLayout.VERTICAL
        sidebar.setPadding(0, px(8), 0, px(8))
        for (l in letters) {
            sidebar.addView(TextView(context).apply {
                text = l
                textSize = 9f
                setTextColor(0x99FFFFFF.toInt())
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(DR_MATCH, 0, 1f))
        }
        sidebar.setOnTouchListener { v, e ->
            if (e.actionMasked == MotionEvent.ACTION_DOWN ||
                e.actionMasked == MotionEvent.ACTION_MOVE
            ) {
                v.parent?.requestDisallowInterceptTouchEvent(true)
                val h = v.height.coerceAtLeast(1)
                val idx = (e.y / h * letters.size).toInt().coerceIn(0, letters.size - 1)
                jumpTo(letters[idx])
            }
            true
        }

        val body = FrameLayout(context)
        body.addView(grid, FrameLayout.LayoutParams(DR_MATCH, DR_MATCH))
        body.addView(sidebar, FrameLayout.LayoutParams(px(22), DR_MATCH, Gravity.END))
        column.addView(body, LinearLayout.LayoutParams(DR_MATCH, 0, 1f).apply {
            topMargin = px(4)
        })

        addView(column, FrameLayout.LayoutParams(DR_MATCH, DR_MATCH))
    }

    private fun applyPadding() {
        column.setPadding(px(16), topInset + px(12), px(16), bottomInset)
    }

    fun setInsets(top: Int, bottom: Int) {
        topInset = top
        bottomInset = bottom
        applyPadding()
    }

    private fun letterOf(a: AppEntry): String {
        val ch = a.label.firstOrNull()?.uppercaseChar()
        return if (ch != null && ch in 'A'..'Z') ch.toString() else "#"
    }

    private fun jumpTo(letter: String) {
        val pos = items.indexOfFirst { letterOf(it) == letter }
        if (pos >= 0) grid.setSelection(pos)
    }

    fun refresh() {
        val q = input.text.toString().trim().lowercase()
        val all = appsProvider()
        items = if (q.isEmpty()) all else all.filter { it.label.lowercase().contains(q) }
        grid.adapter = DrawerAdapter()
        sidebar.visibility = if (q.isEmpty()) View.VISIBLE else View.GONE
    }

    private inner class DrawerAdapter : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            return cellFactory(items[position])
        }
    }

    fun show() {
        input.setText("")
        refresh()
        alpha = 0f
        visibility = View.VISIBLE
        animate().alpha(1f).setDuration(180).start()
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
