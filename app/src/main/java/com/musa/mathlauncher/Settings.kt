package com.musa.mathlauncher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class LauncherSettings(context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    var columns: Int
        get() = prefs.getInt("columns", 4)
        set(v) {
            prefs.edit().putInt("columns", v.coerceIn(3, 6)).apply()
        }

    var iconDp: Int
        get() = prefs.getInt("iconDp", 58)
        set(v) {
            prefs.edit().putInt("iconDp", v.coerceIn(44, 72)).apply()
        }

    var circle: Boolean
        get() = prefs.getBoolean("circle", false)
        set(v) {
            prefs.edit().putBoolean("circle", v).apply()
        }
}

class SettingsPanel(
    context: Context,
    private val settings: LauncherSettings,
    private val onChanged: () -> Unit
) : FrameLayout(context) {

    private val accent = 0xFFFF9F0A.toInt()
    private val colValue = TextView(context)
    private val sizeValue = TextView(context)
    private val squareBtn = TextView(context)
    private val circleBtn = TextView(context)

    private fun px(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    init {
        visibility = View.GONE
        isClickable = true
        setBackgroundColor(0xCC000000.toInt())
        setOnClickListener { hide() }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(22), px(20), px(22), px(20))
            background = GradientDrawable().apply {
                cornerRadius = px(28).toFloat()
                setColor(0xFF1C1C2E.toInt())
            }
            isClickable = true
        }

        card.addView(TextView(context).apply {
            text = "Launcher settings"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })

        card.addView(
            stepper("Columns", colValue,
                { settings.columns -= 1; changed() },
                { settings.columns += 1; changed() }),
            rowParams()
        )
        card.addView(
            stepper("Icon size", sizeValue,
                { settings.iconDp -= 4; changed() },
                { settings.iconDp += 4; changed() }),
            rowParams()
        )

        val shapeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        shapeRow.addView(TextView(context).apply {
            text = "Icon shape"
            textSize = 16f
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        squareBtn.text = "Square"
        circleBtn.text = "Circle"
        for (b in listOf(squareBtn, circleBtn)) {
            b.textSize = 14f
            b.gravity = Gravity.CENTER
            b.setPadding(px(14), 0, px(14), 0)
        }
        squareBtn.setOnClickListener {
            settings.circle = false
            changed()
        }
        circleBtn.setOnClickListener {
            settings.circle = true
            changed()
        }
        shapeRow.addView(squareBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, px(40)
        ).apply { rightMargin = px(8) })
        shapeRow.addView(circleBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, px(40)
        ))
        card.addView(shapeRow, rowParams())

        val done = TextView(context).apply {
            text = "Done"
            textSize = 16f
            setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, px(12), 0, px(12))
            background = GradientDrawable().apply {
                cornerRadius = px(20).toFloat()
                setColor(accent)
            }
            setOnClickListener { hide() }
        }
        card.addView(done, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = px(22) })

        val cardWidth = (context.resources.displayMetrics.widthPixels * 0.86f).toInt()
        addView(card, FrameLayout.LayoutParams(
            cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER
        ))
    }

    private fun rowParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = px(18) }
    }

    private fun changed() {
        refresh()
        onChanged()
    }

    private fun stepButton(t: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = t
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = px(14).toFloat()
                setColor(0x33FFFFFF)
            }
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(px(44), px(40))
        }
    }

    private fun stepper(
        label: String,
        value: TextView,
        onMinus: () -> Unit,
        onPlus: () -> Unit
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(TextView(context).apply {
            text = label
            textSize = 16f
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(stepButton("−", onMinus))
        value.textSize = 16f
        value.setTextColor(Color.WHITE)
        value.gravity = Gravity.CENTER
        value.minWidth = px(52)
        row.addView(value, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        row.addView(stepButton("+", onPlus))
        return row
    }

    private fun styleChip(tv: TextView, selected: Boolean) {
        tv.setTextColor(if (selected) Color.BLACK else Color.WHITE)
        tv.background = GradientDrawable().apply {
            cornerRadius = px(14).toFloat()
            setColor(if (selected) accent else 0x33FFFFFF)
        }
    }

    private fun refresh() {
        colValue.text = settings.columns.toString()
        sizeValue.text = settings.iconDp.toString()
        styleChip(squareBtn, !settings.circle)
        styleChip(circleBtn, settings.circle)
    }

    fun show() {
        refresh()
        alpha = 0f
        visibility = View.VISIBLE
        animate().alpha(1f).setDuration(180).start()
    }

    fun hide() {
        animate().alpha(0f).setDuration(150).withEndAction {
            visibility = View.GONE
        }.start()
    }
}
