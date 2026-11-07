package com.musa.mathlauncher

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

private fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

class AppEntry(val label: String, val pkg: String, val cls: String, val icon: Drawable)

/* ------------------------------------------------------------------ */
/*  Math Clock: every hour mark is an equation that equals the hour    */
/* ------------------------------------------------------------------ */
class MathClockView(context: Context) : View(context) {

    private val d = context.resources.displayMetrics.density

    // index 0 = 1 o'clock ... index 11 = 12 o'clock
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
        val h = cal.get(Calendar.HOUR) // 0..11
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

/* ------------------------------------------------------------------ */
/*  Lock-style screen: Math Clock + date + time, swipe up to unlock    */
/* ------------------------------------------------------------------ */
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

/* ------------------------------------------------------------------ */
/*  The launcher itself                                                */
/* ------------------------------------------------------------------ */
class MainActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var home: LinearLayout
    private lateinit var grid: GridView
    private lateinit var dock: LinearLayout
    private lateinit var lock: LockLayer

    private var apps: List<AppEntry> = emptyList()
    private val prefs by lazy { getSharedPreferences("launcher", MODE_PRIVATE) }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            lock.show(false)
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            loadApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        root = FrameLayout(this)
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF1B1B45.toInt(), 0xFF0A0A16.toInt())
        )

        grid = GridView(this).apply {
            numColumns = 4
            verticalSpacing = dp(18)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            setSelector(android.R.color.transparent)
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        grid.setOnItemClickListener { _, _, pos, _ -> launch(apps[pos]) }
        grid.setOnItemLongClickListener { _, _, pos, _ ->
            toggleDock(apps[pos].pkg, apps[pos].label)
            true
        }

        dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(10), dp(8), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(30).toFloat()
                setColor(0x33FFFFFF)
            }
        }

        home = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(grid, LinearLayout.LayoutParams(MATCH, 0, 1f))
            addView(dock, LinearLayout.LayoutParams(MATCH, WRAP).apply {
                setMargins(dp(12), dp(6), dp(12), dp(12))
            })
        }
        root.addView(home, FrameLayout.LayoutParams(MATCH, MATCH))

        lock = LockLayer(this) { lock.hide() }
        root.addView(lock, FrameLayout.LayoutParams(MATCH, MATCH))

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            home.setPadding(0, b.top + dp(12), 0, b.bottom)
            lock.setInsets(b.top, b.bottom)
            insets
        }

        setContentView(root)
        loadApps()

        ContextCompat.registerReceiver(
            this, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        val pf = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, pf, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(screenOffReceiver)
            unregisterReceiver(packageReceiver)
        } catch (e: Exception) {
            // already unregistered
        }
        super.onDestroy()
    }

    // Back = bring the lock-style screen back (a launcher has nothing to close)
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (lock.visibility != View.VISIBLE) lock.show(true)
    }

    /* ---------------- apps ---------------- */

    private fun loadApps() {
        val pm = packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = pm.queryIntentActivities(q, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map {
                AppEntry(
                    it.loadLabel(pm).toString(),
                    it.activityInfo.packageName,
                    it.activityInfo.name,
                    it.loadIcon(pm)
                )
            }
            .sortedBy { it.label.lowercase() }
        grid.adapter = AppAdapter()
        refreshDock()
    }

    private fun launch(a: AppEntry) {
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(ComponentName(a.pkg, a.cls))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Can't open ${a.label}", Toast.LENGTH_SHORT).show()
        }
    }

    /** iPhone-style rounded-square icon */
    private fun iconView(a: AppEntry): ImageView {
        return ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageDrawable(a.icon.constantState?.newDrawable()?.mutate() ?: a.icon)
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, view.width * 0.225f)
                }
            }
        }
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val a = apps[position]
            val cell = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            cell.addView(iconView(a), LinearLayout.LayoutParams(dp(58), dp(58)))
            val label = TextView(this@MainActivity).apply {
                text = a.label
                textSize = 11f
                setTextColor(Color.WHITE)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setShadowLayer(3f, 0f, 1f, 0x99000000.toInt())
            }
            cell.addView(label, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(6) })
            return cell
        }
    }

    /* ---------------- dock ---------------- */

    private fun defaultDock(): List<String> {
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        )
        val pkgs = linkedSetOf<String>()
        for (i in intents) {
            val p = packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
            if (p != null && p != "android" && apps.any { it.pkg == p }) pkgs.add(p)
        }
        for (a in apps) {
            if (pkgs.size >= 4) break
            pkgs.add(a.pkg)
        }
        return pkgs.toList()
    }

    private fun dockPackages(): List<String> {
        val saved = prefs.getString("dock", null)
        if (saved != null) return saved.split(",").filter { it.isNotEmpty() }
        val def = defaultDock()
        prefs.edit().putString("dock", def.joinToString(",")).apply()
        return def
    }

    private fun refreshDock() {
        dock.removeAllViews()
        val entries = dockPackages().mapNotNull { p -> apps.firstOrNull { it.pkg == p } }
        for (a in entries) {
            val cell = FrameLayout(this)
            val icon = iconView(a)
            icon.setOnClickListener { launch(a) }
            icon.setOnLongClickListener {
                toggleDock(a.pkg, a.label)
                true
            }
            cell.addView(icon, FrameLayout.LayoutParams(dp(58), dp(58), Gravity.CENTER))
            dock.addView(cell, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        dock.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
            }

    private fun toggleDock(pkg: String, label: String) {
        val list = dockPackages().toMutableList()
        if (list.remove(pkg)) {
            Toast.makeText(this, "$label removed from dock", Toast.LENGTH_SHORT).show()
        } else {
            list.add(pkg)
            while (list.size > 4) list.removeAt(0)
            Toast.makeText(this, "$label added to dock", Toast.LENGTH_SHORT).show()
        }
        prefs.edit().putString("dock", list.joinToString(",")).apply()
        refreshDock()
    
