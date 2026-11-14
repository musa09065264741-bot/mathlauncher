package com.musa.mathlauncher

import android.app.Activity
import android.app.AlertDialog
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
import android.view.WindowManager
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
    private lateinit var spotlight: SpotlightPanel
    private lateinit var control: ControlCenter
    private var gestureStartY = -1f
    private var gestureStartX = 0f
    private var lastDownX = 0f
    private var gestureFromTop = false
    private var gestureInGrid = false
    private var topInset = 0

    private val settings by lazy { LauncherSettings(this) }
    private var allApps: List<AppEntry> = emptyList()
    private var apps: List<AppEntry> = emptyList()
    private val prefs by lazy { getSharedPreferences("launcher", MODE_PRIVATE) }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            spotlight.hide()
            control.hide()
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

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
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
            showAppMenu(apps[pos])
            true
        }

        // iPhone-style "Search" pill above the dock
        val searchPill = TextView(this).apply {
            text = "Search"
            textSize = 13f
            setTextColor(0xDDFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(18), 0, dp(18), 0)
            compoundDrawablePadding = dp(6)
            val mag = context.getDrawable(android.R.drawable.ic_menu_search)?.mutate()
            if (mag != null) {
                mag.setTint(0xDDFFFFFF.toInt())
                mag.setBounds(0, 0, dp(14), dp(14))
                setCompoundDrawables(mag, null, null, null)
            }
            background = GradientDrawable().apply {
                cornerRadius = dp(17).toFloat()
                setColor(0x33FFFFFF)
            }
            setOnClickListener { spotlight.show() }
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
            addView(searchPill, LinearLayout.LayoutParams(WRAP, dp(34)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, dp(4), 0, dp(4))
            })
            addView(dock, LinearLayout.LayoutParams(MATCH, WRAP).apply {
                setMargins(dp(12), dp(6), dp(12), dp(12))
            })
        }
        root.addView(home, FrameLayout.LayoutParams(MATCH, MATCH))

        lock = LockLayer(this) { lock.hide() }
        root.addView(lock, FrameLayout.LayoutParams(MATCH, MATCH))

        panel = NotificationPanel(this)
        root.addView(panel, FrameLayout.LayoutParams(MATCH, MATCH))

        spotlight = SpotlightPanel(
            this,
            { q -> spotlightResults(q) },
            { a -> iconView(a) },
            { a ->
                spotlight.hide()
                launch(a)
            }
        )
        root.addView(spotlight, FrameLayout.LayoutParams(MATCH, MATCH))

        control = ControlCenter(this) { settingsPanel.show() }
        root.addView(control, FrameLayout.LayoutParams(MATCH, MATCH))

        statusBar = StatusBarView(this) { onStatusPull() }
        root.addView(statusBar, FrameLayout.LayoutParams(MATCH, WRAP))

        settingsPanel = SettingsPanel(this, settings) { applySettings() }
        root.addView(settingsPanel, FrameLayout.LayoutParams(MATCH, MATCH))

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val b = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            home.setPadding(0, b.top + dp(12), 0, b.bottom)
            lock.setInsets(b.top, b.bottom)
            panel.setInsets(b.top, b.bottom)
            spotlight.setInsets(b.top, maxOf(b.bottom, ime.bottom))
            control.setInsets(b.top, b.bottom)
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

    /** Pulling down the status bar: right side = Control Center, otherwise Notification Centre */
    private fun onStatusPull() {
        if (control.visibility == View.VISIBLE ||
            spotlight.visibility == View.VISIBLE ||
            settingsPanel.visibility == View.VISIBLE
        ) return
        if (lastDownX > root.width * 0.6f) control.show() else panel.show()
    }

    // Swipe down from the top edge: right side = Control Center, otherwise Notification Centre.
    // Swipe down on the apps = Search.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) lastDownX = ev.rawX
        val free = lock.visibility != View.VISIBLE &&
            panel.visibility != View.VISIBLE &&
            settingsPanel.visibility != View.VISIBLE &&
            spotlight.visibility != View.VISIBLE &&
            control.visibility != View.VISIBLE
        if (free) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    gestureStartY = ev.rawY
                    gestureStartX = ev.rawX
                    gestureFromTop = ev.rawY > topInset && ev.rawY < topInset + dp(56)
                    val loc = IntArray(2)
                    grid.getLocationOnScreen(loc)
                    gestureInGrid = ev.rawY >= loc[1] && ev.rawY <= loc[1] + grid.height
                }
                MotionEvent.ACTION_MOVE -> {
                    if (gestureStartY >= 0f && ev.rawY - gestureStartY > dp(80)) {
                        if (gestureFromTop) {
                            gestureStartY = -1f
                            if (gestureStartX > root.width * 0.6f) control.show() else panel.show()
                        } else if (gestureInGrid && !grid.canScrollVertically(-1)) {
                            gestureStartY = -1f
                            spotlight.show()
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> gestureStartY = -1f
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    // Back closes whatever is open, or brings the lock-style screen back
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (settingsPanel.visibility == View.VISIBLE) {
            settingsPanel.hide()
        } else if (control.visibility == View.VISIBLE) {
            control.hide()
        } else if (spotlight.visibility == View.VISIBLE) {
            spotlight.hide()
        } else if (panel.visibility == View.VISIBLE) {
            panel.hide()
        } else if (lock.visibility != View.VISIBLE) {
            lock.show(true)
        }
    }

    private fun applySettings() {
        grid.numColumns = settings.columns
        applyFilter()
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
        allApps = listOf(settingsEntry()) + list
        applyFilter()
        refreshDock()
    }

    /** Visible apps = everything except the apps you hid */
    private fun applyFilter() {
        val hidden = settings.hidden
        apps = allApps.filter { a -> a.pkg == SETTINGS_PKG || !hidden.contains(a.pkg) }
        grid.adapter = AppAdapter()
    }

    /** What the Spotlight search shows: dock apps as suggestions, or matches for the text */
    private fun spotlightResults(q: String): List<AppEntry> {
        if (q.isEmpty()) {
            return dockPackages().mapNotNull { p -> apps.firstOrNull { it.pkg == p } }
        }
        val l = q.lowercase()
        return apps
            .filter { it.label.lowercase().contains(l) }
            .sortedBy { !it.label.lowercase().startsWith(l) }
    }

    private fun showAppMenu(a: AppEntry) {
        if (a.pkg == SETTINGS_PKG) return
        val inDock = dockPackages().contains(a.pkg)
        val items = arrayOf(
            if (inDock) "Remove from dock" else "Add to dock",
            "Hide app"
        )
        AlertDialog.Builder(this)
            .setTitle(a.label)
            .setItems(items) { _, which ->
                if (which == 0) {
                    toggleDock(a.pkg, a.label)
                } else {
                    hideApp(a)
                }
            }
            .show()
    }

    private fun hideApp(a: AppEntry) {
        settings.hidden = settings.hidden + a.pkg
        applyFilter()
        Toast.makeText(
            this,
            "${a.label} hidden. Show it again in Launcher settings.",
            Toast.LENGTH_LONG
        ).show()
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
            if (p != null && p != "android" && allApps.any { it.pkg == p }) pkgs.add(p)
        }
        for (a in allApps) {
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
        val entries = dockPackages().mapNotNull { p -> allApps.firstOrNull { it.pkg == p } }
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
