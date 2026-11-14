package com.musa.mathlauncher

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

private const val CC_MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val CC_WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Tall iPhone-style slider: drag up and down */
class VerticalSlider(
    context: Context,
    private val glyph: String,
    private val onChange: (Float) -> Unit
) : View(context) {

    private var level = 0.5f
    private val density = resources.displayMetrics.density
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40FFFFFF }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF7C7C8A.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 26f * density
    }
    private val rect = RectF()
    private val clip = Path()

    fun setLevel(v: Float) {
        level = v.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                if (height > 0) {
                    level = (1f - e.y / height).coerceIn(0f, 1f)
                    invalidate()
                    onChange(level)
                }
            }
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = 24f * density
        rect.set(0f, 0f, w, h)
        c.drawRoundRect(rect, r, r, bgPaint)
        clip.reset()
        clip.addRoundRect(rect, r, r, Path.Direction.CW)
        c.save()
        c.clipPath(clip)
        c.drawRect(0f, h * (1f - level), w, h, fillPaint)
        c.restore()
        c.drawText(glyph, w / 2f, h - 22f * density, textPaint)
    }
}

class ControlCenter(
    context: Context,
    private val onOpenSettings: () -> Unit
) : FrameLayout(context) {

    private val blue = 0xFF0A84FF.toInt()
    private val green = 0xFF30D158.toInt()
    private val orange = 0xFFFF9F0A.toInt()
    private val red = 0xFFFF453A.toInt()

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val connectivity =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val cameras = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var torchId: String? = null
    private var torchOn = false
    private var askedWrite = false
    private var brightMax = 255
    private var topInset = 0
    private var bottomInset = 0
    private var downY = 0f

    private val airplane = TextView(context)
    private val mobile = TextView(context)
    private val wifi = TextView(context)
    private val bluetooth = TextView(context)
    private val torchTile = TextView(context)
    private val silentTile = TextView(context)
    private val column = LinearLayout(context)
    private val brightness: VerticalSlider
    private val volume: VerticalSlider

    private fun px(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) {
                torchOn = enabled
                styleTile(torchTile, torchOn, Color.WHITE, Color.BLACK)
            }
        }
    }

    init {
        visibility = View.GONE
        isClickable = true
        setBackgroundColor(0xE60B0B18.toInt())

        try {
            torchId = cameras.cameraIdList.firstOrNull { id ->
                cameras.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            torchId = null
        }

        brightness = VerticalSlider(context, "🔆") { v -> setBrightness(v) }
        volume = VerticalSlider(context, "🔊") { v ->
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v * max + 0.5f).toInt(), 0)
        }

        column.orientation = LinearLayout.VERTICAL
        applyPadding()

        val row1 = LinearLayout(context)
        row1.orientation = LinearLayout.HORIZONTAL
        row1.addView(
            connectivityCard(),
            LinearLayout.LayoutParams(0, px(150), 1f).apply { rightMargin = px(6) }
        )
        row1.addView(
            shortcutGrid(),
            LinearLayout.LayoutParams(0, px(150), 1f).apply { leftMargin = px(6) }
        )
        column.addView(row1, LinearLayout.LayoutParams(CC_MATCH, CC_WRAP))

        val row2 = LinearLayout(context)
        row2.orientation = LinearLayout.HORIZONTAL
        val sliders = LinearLayout(context)
        sliders.orientation = LinearLayout.HORIZONTAL
        sliders.addView(
            brightness,
            LinearLayout.LayoutParams(0, CC_MATCH, 1f).apply { rightMargin = px(6) }
        )
        sliders.addView(
            volume,
            LinearLayout.LayoutParams(0, CC_MATCH, 1f).apply { leftMargin = px(6) }
        )
        row2.addView(
            sliders,
            LinearLayout.LayoutParams(0, px(150), 1f).apply { rightMargin = px(6) }
        )
        val wide = LinearLayout(context)
        wide.orientation = LinearLayout.VERTICAL
        wide.addView(
            wideTile("📷", "Camera") { open(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) },
            LinearLayout.LayoutParams(CC_MATCH, 0, 1f).apply { bottomMargin = px(6) }
        )
        wide.addView(
            wideTile("⚙", "Launcher") {
                hide()
                onOpenSettings()
            },
            LinearLayout.LayoutParams(CC_MATCH, 0, 1f).apply { topMargin = px(6) }
        )
        row2.addView(
            wide,
            LinearLayout.LayoutParams(0, px(150), 1f).apply { leftMargin = px(6) }
        )
        column.addView(
            row2,
            LinearLayout.LayoutParams(CC_MATCH, CC_WRAP).apply { topMargin = px(12) }
        )

        addView(column, FrameLayout.LayoutParams(CC_MATCH, CC_MATCH))
    }

    /* ---------------- building the tiles ---------------- */

    private fun makeCard(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            background = GradientDrawable().apply {
                cornerRadius = px(24).toFloat()
                setColor(0x40FFFFFF)
            }
        }
    }

    private fun circleCell(v: TextView, label: String, size: Float, onClick: () -> Unit): FrameLayout {
        v.text = label
        v.textSize = size
        v.gravity = Gravity.CENTER
        v.setTextColor(Color.WHITE)
        v.typeface = Typeface.DEFAULT_BOLD
        v.setOnClickListener { onClick() }
        styleCircle(v, false, blue)
        val cell = FrameLayout(context)
        cell.addView(v, FrameLayout.LayoutParams(px(54), px(54), Gravity.CENTER))
        return cell
    }

    private fun connectivityCard(): LinearLayout {
        val card = makeCard()
        card.setPadding(px(8), px(8), px(8), px(8))
        val r1 = LinearLayout(context)
        r1.orientation = LinearLayout.HORIZONTAL
        val r2 = LinearLayout(context)
        r2.orientation = LinearLayout.HORIZONTAL
        r1.addView(
            circleCell(airplane, "✈", 24f) { open(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)) },
            LinearLayout.LayoutParams(0, CC_MATCH, 1f)
        )
        r1.addView(
            circleCell(mobile, "📶", 22f) { open(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)) },
            LinearLayout.LayoutParams(0, CC_MATCH, 1f)
        )
        r2.addView(
            circleCell(wifi, "Wi-Fi", 12f) { openWifi() },
            LinearLayout.LayoutParams(0, CC_MATCH, 1f)
        )
        r2.addView(
            circleCell(bluetooth, "BT", 15f) { open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
            LinearLayout.LayoutParams(0, CC_MATCH, 1f)
        )
        card.addView(r1, LinearLayout.LayoutParams(CC_MATCH, 0, 1f))
        card.addView(r2, LinearLayout.LayoutParams(CC_MATCH, 0, 1f))
        return card
    }

    private fun stateTile(tv: TextView, glyph: String, onClick: () -> Unit): TextView {
        tv.text = glyph
        tv.textSize = 26f
        tv.gravity = Gravity.CENTER
        tv.setOnClickListener { onClick() }
        styleTile(tv, false, Color.WHITE, Color.BLACK)
        return tv
    }

    private fun tileParams(leftTile: Boolean): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, CC_MATCH, 1f).apply {
            if (leftTile) rightMargin = px(6) else leftMargin = px(6)
        }
    }

    private fun shortcutGrid(): LinearLayout {
        val col = LinearLayout(context)
        col.orientation = LinearLayout.VERTICAL
        val a = LinearLayout(context)
        a.orientation = LinearLayout.HORIZONTAL
        val b = LinearLayout(context)
        b.orientation = LinearLayout.HORIZONTAL

        a.addView(stateTile(torchTile, "🔦") { toggleTorch() }, tileParams(true))
        a.addView(stateTile(silentTile, "🔕") { toggleSilent() }, tileParams(false))
        b.addView(
            stateTile(TextView(context), "⏲") { open(Intent(AlarmClock.ACTION_SET_TIMER)) },
            tileParams(true)
        )
        b.addView(
            stateTile(TextView(context), "🔢") {
                open(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR))
            },
            tileParams(false)
        )

        col.addView(
            a,
            LinearLayout.LayoutParams(CC_MATCH, 0, 1f).apply { bottomMargin = px(6) }
        )
        col.addView(
            b,
            LinearLayout.LayoutParams(CC_MATCH, 0, 1f).apply { topMargin = px(6) }
        )
        return col
    }

    private fun wideTile(glyph: String, label: String, onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(px(16), 0, px(16), 0)
        row.background = GradientDrawable().apply {
            cornerRadius = px(20).toFloat()
            setColor(0x40FFFFFF)
        }
        row.setOnClickListener { onClick() }
        row.addView(TextView(context).apply {
            text = glyph
            textSize = 22f
            setTextColor(Color.WHITE)
        })
        row.addView(
            TextView(context).apply {
                text = label
                textSize = 15f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            },
            LinearLayout.LayoutParams(CC_WRAP, CC_WRAP).apply { leftMargin = px(10) }
        )
        return row
    }

    private fun styleCircle(tv: TextView, active: Boolean, color: Int) {
        tv.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (active) color else 0x55FFFFFF)
        }
    }

    private fun styleTile(tv: TextView, active: Boolean, activeBg: Int, activeText: Int) {
        tv.setTextColor(if (active) activeText else Color.WHITE)
        tv.background = GradientDrawable().apply {
            cornerRadius = px(20).toFloat()
            setColor(if (active) activeBg else 0x40FFFFFF)
        }
    }

    /* ---------------- actions ---------------- */

    private fun say(t: String) {
        Toast.makeText(context, t, Toast.LENGTH_SHORT).show()
    }

    private fun open(intent: Intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            hide()
        } catch (e: Exception) {
            say("Can't open that here")
        }
    }

    private fun openWifi() {
        if (Build.VERSION.SDK_INT >= 29) {
            open(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
        } else {
            open(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
    }

    private fun toggleTorch() {
        val id = torchId
        if (id == null) {
            say("No flashlight found")
            return
        }
        try {
            cameras.setTorchMode(id, !torchOn)
        } catch (e: Exception) {
            say("Flashlight is busy")
        }
    }

    private fun toggleSilent() {
        try {
            audio.ringerMode =
                if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                    AudioManager.RINGER_MODE_VIBRATE
                } else {
                    AudioManager.RINGER_MODE_NORMAL
                }
            refresh()
        } catch (e: Exception) {
            say("Allow Do Not Disturb access to change this")
        }
    }

    private fun setBrightness(v: Float) {
        if (!Settings.System.canWrite(context)) {
            if (!askedWrite) {
                askedWrite = true
                say("Allow \"Modify system settings\" for Math Launcher, then come back")
                open(
                    Intent(
                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:" + context.packageName)
                    )
                )
            }
            return
        }
        try {
            val b = (v * brightMax).toInt().coerceIn(1, brightMax)
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, b)
        } catch (e: Exception) {
            say("Can't change brightness")
        }
    }

    /* ---------------- state ---------------- */

    private fun refresh() {
        val airOn = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        } catch (e: Exception) {
            false
        }

        var wifiOn = false
        var cellOn = false
        try {
            @Suppress("DEPRECATION")
            for (n in connectivity.allNetworks) {
                val caps = connectivity.getNetworkCapabilities(n) ?: continue
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifiOn = true
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) cellOn = true
            }
        } catch (e: Exception) {
            // leave both off
        }

        styleCircle(airplane, airOn, orange)
        styleCircle(mobile, cellOn && !airOn, green)
        styleCircle(wifi, wifiOn, blue)
        styleCircle(bluetooth, false, blue)
        styleTile(torchTile, torchOn, Color.WHITE, Color.BLACK)
        val silent = audio.ringerMode != AudioManager.RINGER_MODE_NORMAL
        styleTile(silentTile, silent, red, Color.WHITE)

        val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        volume.setLevel(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol)

        val cur = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Exception) {
            128
        }
        brightMax = if (cur > 255) 2047 else 255
        brightness.setLevel(cur.toFloat() / brightMax)
    }

    /* ---------------- show / hide ---------------- */

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        try {
            cameras.registerTorchCallback(torchCallback, null)
        } catch (e: Exception) {
            // no flashlight updates
        }
    }

    override fun onDetachedFromWindow() {
        try {
            cameras.unregisterTorchCallback(torchCallback)
        } catch (e: Exception) {
            // already gone
        }
        super.onDetachedFromWindow()
    }

    private fun applyPadding() {
        column.setPadding(px(20), topInset + px(28), px(20), bottomInset)
    }

    fun setInsets(top: Int, bottom: Int) {
        topInset = top
        bottomInset = bottom
        applyPadding()
    }

    // Swipe up or tap the empty area to close
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> downY = e.y
            MotionEvent.ACTION_UP -> {
                if (downY - e.y > px(60) || abs(e.y - downY) < px(10)) hide()
            }
        }
        return true
    }

    fun show() {
        refresh()
        askedWrite = false
        alpha = 0f
        visibility = View.VISIBLE
        animate().alpha(1f).setDuration(180).start()
    }

    fun hide() {
        if (visibility != View.VISIBLE) return
        animate().alpha(0f).setDuration(150).withEndAction {
            visibility = View.GONE
        }.start()
    }
}
