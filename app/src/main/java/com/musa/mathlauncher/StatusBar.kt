package com.musa.mathlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.text.format.DateFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private class BatteryIconView(context: Context) : View(context) {
    private val d = context.resources.displayMetrics.density
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * d
        color = 0xAAFFFFFF.toInt()
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val nub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xAAFFFFFF.toInt()
    }
    private var level = 100
    private var charging = false

    fun set(l: Int, c: Boolean) {
        level = l
        charging = c
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((26 * d).toInt(), (13 * d).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val bodyW = w - 3f * d
        canvas.drawRoundRect(RectF(0.6f * d, 0.6f * d, bodyW, h - 0.6f * d), 3.5f * d, 3.5f * d, outline)
        fill.color = when {
            charging -> 0xFF30D158.toInt()
            level <= 20 -> 0xFFFF453A.toInt()
            else -> Color.WHITE
        }
        val frac = level.coerceIn(0, 100) / 100f
        val right = 2.4f * d + (bodyW - 4.8f * d) * frac
        if (right > 2.4f * d) {
            canvas.drawRoundRect(RectF(2.4f * d, 2.4f * d, right, h - 2.4f * d), 2f * d, 2f * d, fill)
        }
        canvas.drawRoundRect(RectF(bodyW + 0.8f * d, h * 0.32f, bodyW + 2.4f * d, h * 0.68f), 1f * d, 1f * d, nub)
    }
}

class StatusBarView(context: Context, private val onPullDown: () -> Unit) : LinearLayout(context) {

    private val timeView = TextView(context)
    private val countView = TextView(context)
    private val netView = TextView(context)
    private val batteryText = TextView(context)
    private val batteryIcon = BatteryIconView(context)

    private var level = 100
    private var charging = false
    private var downY = 0f
    private var fired = false

    private fun px(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private val tick = object : Runnable {
        override fun run() {
            update()
            postDelayed(this, 1000L - System.currentTimeMillis() % 1000L)
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            readBattery(i)
            update()
        }
    }

    init {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(px(22), 0, px(22), 0)

        timeView.textSize = 15f
        timeView.setTextColor(Color.WHITE)
        timeView.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        addView(timeView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        countView.textSize = 12f
        countView.setTextColor(0xFFFF9F0A.toInt())
        countView.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        addView(countView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { rightMargin = px(10) })

        netView.textSize = 12f
        netView.setTextColor(Color.WHITE)
        addView(netView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { rightMargin = px(10) })

        batteryText.textSize = 12f
        batteryText.setTextColor(Color.WHITE)
        addView(batteryText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { rightMargin = px(5) })

        addView(batteryIcon, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        update()
    }

    fun setBarHeight(h: Int) {
        val lp = layoutParams ?: return
        lp.height = maxOf(h, px(24))
        layoutParams = lp
    }

    private fun readBattery(i: Intent?) {
        if (i == null) return
        val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (l >= 0 && s > 0) level = l * 100 / s
        val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        charging = st == BatteryManager.BATTERY_STATUS_CHARGING ||
            st == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun update() {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
        timeView.text = SimpleDateFormat(pattern, Locale.getDefault()).format(Date())

        val n = NotificationStore.items.size
        if (n > 0) {
            countView.text = "🔔 $n"
            countView.visibility = View.VISIBLE
        } else {
            countView.visibility = View.GONE
        }

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = try {
            cm.getNetworkCapabilities(cm.activeNetwork)
        } catch (e: Exception) {
            null
        }
        netView.text = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
            else -> "Online"
        }

        batteryText.text = "$level%"
        batteryIcon.set(level, charging)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val sticky = ContextCompat.registerReceiver(
            context, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        readBattery(sticky)
        post(tick)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // already unregistered
        }
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = e.rawY
                fired = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!fired && e.rawY - downY > px(30)) {
                    fired = true
                    onPullDown()
                }
            }
        }
        return true
    }
}
