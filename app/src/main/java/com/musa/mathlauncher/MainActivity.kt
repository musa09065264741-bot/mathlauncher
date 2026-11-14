package com.musa.mathlauncher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.TextUtils
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

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private const val SETTINGS_PKG = "com.musa.mathlauncher.settings"

private fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

class AppEntry(val label: String, val pkg: String, val cls: String, val icon: Drawable)

class MainActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var home: LinearLayout
    private lateinit var grid: GridView
    private lateinit var dock: LinearLayout
    private lateinit var lock: LockLayer
    private lateinit var panel: NotificationPanel
    private lateinit var statusBar: StatusBarView
    private lateinit var settingsPanel: SettingsPanel
    private var gestureStartY = -1f
    private var topInset = 0

    private val settings by lazy { LauncherSettings(this) }
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

    private fun iconPx(): Int = dp(settings.iconDp)

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
            numColumns = settings.columns
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

        panel = NotificationPanel(this)
        root.addView(panel, FrameLayout.LayoutParams(MATCH, MATCH))

        statusBar = StatusBarView(this) { panel.show() }
        root.addView(statusBar, FrameLayout.LayoutParams(MATCH, WRAP))

        settingsPanel = SettingsPanel(this, settings) { applySettings() }
        root.addView(settingsPanel, FrameLayout.LayoutParams(MATCH, MATCH))

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val b = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
            home.setPadding(0, b.top + dp(12), 0, b.bottom)
            lock.setInsets(b.top, b.bottom)
            panel.setInsets(b.top, b.bottom)
            topInset = b.top
            statusBar.setBarHeight(b.top)
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

    override fun onResume() {
        super.onResume()
        panel.render()
    }

    private fun hideSystemStatus() {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.statusBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemStatus()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (lock.visibility != View.VISIBLE &&
            panel.visibility != View.VISIBLE &&
            settingsPanel.visibility != View.VISIBLE
        ) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN ->
                    gestureStartY = if (ev.rawY > topInset && ev.rawY < topInset + dp(160)) ev.rawY else -1f
                MotionEvent.ACTION_MOVE ->
                    if (gestureStartY >= 0f && ev.rawY - gestureStartY > dp(80)) {
                        gestureStartY = -1f
                        panel.show()
                    }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    // Back closes settings or the notification panel, or brings the lock-style screen back
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (settingsPanel.visibility == View.VISIBLE) {
            settingsPanel.hide()
        } else if (panel.visibility == View.VISIBLE) {
            panel.hide()
        } else if (lock.visibility != View.VISIBLE) {
            lock.show(true)
        }
    }

    private fun applySettings() {
        grid.numColumns = settings.columns
        grid.adapter = AppAdapter()
        refreshDock()
    }

    /* ---------------- apps ---------------- */

    private fun settingsEntry(): AppEntry {
        val bg = GradientDrawable().apply { setColor(0xFF3A3A4C.toInt()) }
        val gear = ContextCompat.getDrawable(this, android.R.drawable.ic_menu_preferences)
        val icon: Drawable = if (gear != null) {
            LayerDrawable(arrayOf(bg, gear)).apply {
                setLayerInset(1, dp(14), dp(14), dp(14), dp(14))
            }
        } else {
            bg
        }
        return AppEntry("Launcher", SETTINGS_PKG, "", icon)
    }

    private fun loadApps() {
        val pm = packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(q, 0)
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
        apps = listOf(settingsEntry()) + list
        grid.adapter = AppAdapter()
        refreshDock()
    }

    private fun launch(a: AppEntry) {
        if (a.pkg == SETTINGS_PKG) {
            settingsPanel.show()
            return
        }
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

    /** iPhone-style icon: rounded square or circle */
    private fun iconView(a: AppEntry): ImageView {
        return ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageDrawable(a.icon.constantState?.newDrawable()?.mutate() ?: a.icon)
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    if (settings.circle) {
                        outline.setOval(0, 0, view.width, view.height)
                    } else {
                        outline.setRoundRect(0, 0, view.width, view.height, view.width * 0.225f)
                    }
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
            cell.addView(iconView(a), LinearLayout.LayoutParams(iconPx(), iconPx()))
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
            if (a.pkg != SETTINGS_PKG) pkgs.add(a.pkg)
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
            cell.addView(icon, FrameLayout.LayoutParams(iconPx(), iconPx(), Gravity.CENTER))
            dock.addView(cell, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        dock.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun toggleDock(pkg: String, label: String) {
        if (pkg == SETTINGS_PKG) return
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
    }
}
