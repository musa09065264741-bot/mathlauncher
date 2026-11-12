package com.musa.mathlauncher

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.format.DateFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

class MathClockView(context: Context) : View(context) {

    private val d = context.resources.displayMetrics.density

    private val equations = arrayOf(
        "5-4", "1+1", "9÷3", "2²", "10÷2", "3×2",
        "14÷2", "2³", "3²", "5×2", "22÷2", "4×3"
    )

    private val accent = 0xFFFF9F0A.toInt()

    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x14FFFFFF
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x40FFFFFF
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * d
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val hourPaint = handPaint(Color.WHITE, 5.5f)
    private val minutePaint = handPaint(Color.WHITE, 3.5f)
    private val secondPaint = handPaint(accent, 1.8f)
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }

    private fun handPaint(c: Int, w: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = c
        style = Paint.Style.STROKE
        strokeWidth = w * d
        strokeCap = Paint.Cap.ROUND
    }

    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, 1000L - System.currentTimeMillis() % 1000L)
        }
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        removeCallbacks(tick)
        if (isVisible) post(tick)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val r = min(w, h) / 2f
        textPaint.textSize = r * 0.125f
        highlightPaint.textSize = r * 0.14f
    }

    private fun drawHand(c: Canvas, cx: Float, cy: Float, angle: Float, len: Float, tail: Float, p: Paint) {
        c.save()
        c.rotate(angle, cx, cy)
        c.drawLine(cx, cy + tail, cx, cy - len, p)
        c.restore()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(cx, cy) - 4f * d

        canvas.drawCircle(cx, cy, r, facePaint)
        canvas.drawCircle(cx, cy, r, ringPaint)

        val cal = Calendar.getInstance()
        val h = cal.get(Calendar.HOUR)
        val m = cal.get(Calendar.MINUTE)
        val s = cal.get(Calendar.SECOND)
        val currentMark = if (h == 0) 12 else h

        for (i in 1..12) {
            val ang = Math.toRadians(i * 30.0 - 90.0)
            val x = cx + (r * 0.80f * cos(ang)).toFloat()
            val y = cy + (r * 0.80f * sin(ang)).toFloat()
            val p = if (i == currentMark) highlightPaint else textPaint
            val ty = y - (p.descent() + p.ascent()) / 2f
            canvas.drawText(equations[i - 1], x, ty, p)
        }

        val hourAngle = (h + m / 60f) * 30f
        val minuteAngle = (m + s / 60f) * 6f
        val secondAngle = s * 6f

        drawHand(canvas, cx, cy, hourAngle, r * 0.45f, 0f, hourPaint)
        drawHand(canvas, cx, cy, minuteAngle, r * 0.62f, 0f, minutePaint)
        drawHand(canvas, cx, cy, secondAngle, r * 0.70f, r * 0.12f, secondPaint)
        canvas.drawCircle(cx, cy, 5f * d, centerPaint)
    }
}

class LockLayer(context: Context, private val onUnlock: () -> Unit) : FrameLayout(context) {

    private val d = context.resources.displayMetrics.density
    private val column = LinearLayout(context)
    private val dateView = TextView(context)
    private val timeView = TextView(context)
    private val hintView = TextView(context)
    private val clock = MathClockView(context)

    private var downY = 0f
    private var lastY = 0f
    private var lastT = 0L
    private var vy = 0f
    private var hintAnim: ObjectAnimator? = null

    private val tick = object : Runnable {
        override fun run() {
            updateTexts()
            postDelayed(this, 1000L - System.currentTimeMillis() % 1000L)
        }
    }

    init {
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF0E0E2C.toInt(), 0xFF000000.toInt())
        )

        column.orientation = LinearLayout.VERTICAL
        column.gravity = Gravity.CENTER_HORIZONTAL

        dateView.textSize = 18f
        dateView.setTextColor(0xCCFFFFFF.toInt())
        dateView.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        timeView.textSize = 54f
        timeView.setTextColor(Color.WHITE)
        timeView.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)

        hintView.textSize = 14f
        hintView.setTextColor(0x99FFFFFF.toInt())
        hintView.text = "↑  Swipe up to open"

        val size = (context.resources.displayMetrics.widthPixels * 0.84f).toInt()

        column.addView(dateView, LinearLayout.LayoutParams(WRAP, WRAP).apply {
            topMargin = (40 * d).toInt()
        })
        column.addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
        column.addView(clock, LinearLayout.LayoutParams(size, size))
        column.addView(timeView, LinearLayout.LayoutParams(WRAP, WRAP).apply {
            topMargin = (16 * d).toInt()
        })
        column.addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
        column.addView(hintView, LinearLayout.LayoutParams(WRAP, WRAP).apply {
            bottomMargin = (24 * d).toInt()
        })

        addView(column, FrameLayout.LayoutParams(MATCH, MATCH))
        updateTexts()
    }

    fun setInsets(top: Int, bottom: Int) {
        column.setPadding(0, top, 0, bottom)
    }

    private fun updateTexts() {
        val now = Date()
        dateView.text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now)
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
        timeView.text = SimpleDateFormat(pattern, Locale.getDefault()).format(now)
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        removeCallbacks(tick)
        if (isVisible) {
            post(tick)
            if (hintAnim == null) {
                hintAnim = ObjectAnimator.ofFloat(hintView, View.TRANSLATION_Y, 0f, -8f * d).apply {
                    duration = 900
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                }
            }
            hintAnim?.start()
        } else {
            hintAnim?.cancel()
            hintView.translationY = 0f
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        hintAnim?.cancel()
        super.onDetachedFromWindow()
    }

    fun hide() {
        animate().translationY(-height.toFloat()).setDuration(220).withEndAction {
            visibility = View.GONE
        }.start()
    }

    fun show(animated: Boolean) {
        animate().cancel()
        visibility = View.VISIBLE
        if (animated && height > 0) {
            translationY = -height.toFloat()
            animate().translationY(0f).setDuration(250).start()
        } else {
            translationY = 0f
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animate().cancel()
                downY = e.rawY
                lastY = e.rawY
                lastT = e.eventTime
                vy = 0f
            }
            MotionEvent.ACTION_MOVE -> {
                val dt = e.eventTime - lastT
                if (dt > 0) {
                    vy = 0.6f * vy + 0.4f * ((e.rawY - lastY) / dt * 1000f)
                }
                lastY = e.rawY
                lastT = e.eventTime
                translationY = min(0f, e.rawY - downY)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dy = e.rawY - downY
                if (e.actionMasked == MotionEvent.ACTION_UP && (dy < -height * 0.2f || vy < -1200f)) {
                    onUnlock()
                } else {
                    animate().translationY(0f).setDuration(200).start()
                }
            }
        }
        return true
    }
}
