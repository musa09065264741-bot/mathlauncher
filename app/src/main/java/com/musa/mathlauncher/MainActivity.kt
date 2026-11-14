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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.abs

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private const val SETTINGS_PKG = "com.musa.mathlauncher.settings"

private fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

class AppEntry(val label: String, val pkg: String, val cls: String, val icon: Drawable)

class MainActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var home: LinearLayout
    private lateinit var pager: HomePager
    private lateinit var dots: LinearLayout
    private lateinit var dock: LinearLayout
    private lateinit var lock: LockLayer
    private lateinit var panel: NotificationPanel
    private lateinit var statusBar: StatusBarView
    private lateinit var settingsPanel: SettingsPanel
    private lateinit var spotlight: SpotlightPanel
    private lateinit var control: ControlCenter
    private lateinit var drawer: AppDrawer
    private var gestureStartY = -1f
    private var gestureStartX = 0f
    private var lastDownX = 0f
    private var gestureFromTop = false
    private var gestureInPager = false
    private var topInset = 0

    private val settings by lazy { LauncherSettings(this) }
    private var allApps: List<AppEntry> = emptyList()
    private var apps: List<AppEntry> = emptyList()
    private val prefs by lazy { getSharedPreferences("launcher", MODE_PRIVATE) }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            spotlight.hide()
            control.hide()
            drawer.hide()
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

        pager = HomePager(this)
        pager.onPage = { cur, total -> updateDots(cur, total) }

        // Wide search bar above the page dots
        val searchBar = TextView(this).apply {
            text = "Search"
            textSize = 15f
            setTextColor(0xCCFFFFFF.toInt())
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(18), 0)
            compoundDrawablePadding = dp(10)
            val mag = context.getDrawable(android.R.drawable.ic_menu_search)?.mutate()
            if (mag != null) {
                mag.setTint(0xCCFFFFFF.toInt())
                mag.setBounds(0, 0, dp(18), dp(18))
                setCompoundDrawables(mag, null, null, null)
            }
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(0x33FFFFFF)
            }
            setOnClickListener { spotlight.show() }
        }

        dots = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
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
            addView(pager, LinearLayout.LayoutParams(MATCH, 0, 1f))
            addView(searchBar, LinearLayout.LayoutParams(MATCH, dp(46)).apply {
                setMargins(dp(16), dp(6), dp(16), dp(8))
            })
            addView(dots, LinearLayout.LayoutParams(MATCH, dp(14)))
            addView(dock, LinearLayout.LayoutParams(MATCH, WRAP).apply {
                setMargins(dp(12), dp(6), dp(12), dp(12))
            })
        }
        root.addView(home, FrameLayout.LayoutParams(MATCH, MATCH))

        lock = LockLayer(this) { lock.hide() }
        root.addView(lock, FrameLayout.LayoutParams(MATCH, MATCH))

        panel = NotificationPanel(this)
        root.addView(panel, FrameLayout.LayoutParams(MATCH, MATCH))

        drawer = AppDrawer(
            this,
            { apps.filter { it.pkg != SETTINGS_PKG } },
            { a -> appCell(a) },
            { a ->
                drawer.hide()
                launch(a)
            },
            { a -> showAppMenu(a) },
            {
                drawer.hide()
                settingsPanel.show()
            }
        )
        root.addView(drawer, FrameLayout.LayoutParams(MATCH, MATCH))

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
            val bottomKeys = maxOf(b.bottom, ime.bottom)
            home.setPadding(0, b.top + dp(12), 0, b.bottom)
            lock.setInsets(b.top, b.bottom)
            panel.setInsets(b.top, b.bottom)
            drawer.setInsets(b.top, bottomKeys)
            spotlight.setInsets(b.top, bottomKeys)
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
            drawer.visibility == View.VISIBLE ||
            settingsPanel.visibility == View.VISIBLE
        ) return
        if (lastDownX > root.width * 0.6f) control.show() else panel.show()
    }

    // Top edge, swipe down: right side = Control Center, otherwise Notification Centre.
    // On the apps: swipe down = Search, swipe up = App drawer.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) lastDownX = ev.rawX
        val free = lock.visibility != View.VISIBLE &&
            panel.visibility != View.VISIBLE &&
            settingsPanel.visibility != View.VISIBLE &&
            spotlight.visibility != View.VISIBLE &&
            control.visibility != View.VISIBLE &&
            drawer.visibility != View.VISIBLE
        if (free) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    gestureStartY = ev.rawY
                    gestureStartX = ev.rawX
                    gestureFromTop = ev.rawY > topInset && ev.rawY < topInset + dp(56)
                    val loc = IntArray(2)
                    pager.getLocationOnScreen(loc)
                    gestureInPager = ev.rawY >= loc[1] && ev.rawY <= loc[1] + pager.height
                }
                MotionEvent.ACTION_MOVE -> {
                    if (gestureStartY >= 0f) {
                        val dy = ev.rawY - gestureStartY
                        val dx = ev.rawX - gestureStartX
                        if (abs(dy) > abs(dx) * 1.5f) {
                            if (dy > dp(80)) {
                                if (gestureFromTop) {
                                    gestureStartY = -1f
                                    if (gestureStartX > root.width * 0.6f) {
                                        control.show()
                                    } else {
                                        panel.show()
                                    }
                                } else if (gestureInPager) {
                                    gestureStartY = -1f
                                    spotlight.show()
                                }
                            } else if (dy < -dp(80) && gestureInPager) {
                                gestureStartY = -1f
                                drawer.show()
                            }
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
        } else if (drawer.visibility == View.VISIBLE) {
            drawer.hide()
        } else if (spotlight.visibility == View.VISIBLE) {
            spotlight.hide()
        } else if (panel.visibility == View.VISIBLE) {
            panel.hide()
        } else if (lock.visibility != View.VISIBLE) {
            lock.show(true)
        } else {
            pager.goToFirstPage()
        }
    }

    private fun applySettings() {
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
        refreshHome()
        drawer.refresh()
    }

    /** What the Spotlight search shows: dock apps as suggestions, or matches for the text */
    private fun spotlightResults(q: String): List<AppEntry> {
        if (q.isEmpty()) {
            return dockPackages().take(3).mapNotNull { p -> apps.firstOrNull { it.pkg == p } }
        }
        val l = q.lowercase()
        return apps
            .filter { it.label.lowercase().contains(l) }
            .sortedBy { !it.label.lowercase().startsWith(l) }
    }

    /** Long-press menu, used on the home screen and in the app drawer */
    private fun showAppMenu(a: AppEntry) {
        val labels = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        val onHome = homePackages().contains(a.pkg)
        labels.add(if (onHome) "Remove from home" else "Add to home")
        actions.add { toggleHome(a) }
        if (a.pkg != SETTINGS_PKG) {
            val inDock = dockPackages().take(3).contains(a.pkg)
            labels.add(if (inDock) "Remove from dock" else "Add to dock")
            actions.add { toggleDock(a.pkg, a.label) }
            labels.add("Hide app")
            actions.add { hideApp(a) }
        }
        AlertDialog.Builder(this)
            .setTitle(a.label)
            .setItems(labels.toTypedArray()) { _, which ->
                actions[which]()
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

    /** Icon with its name underneath */
    private fun appCell(a: AppEntry): LinearLayout {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        cell.addView(iconView(a), LinearLayout.LayoutParams(iconPx(), iconPx()))
        val label = TextView(this).apply {
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

    /* ---------------- home screen ---------------- */

    private fun homePackages(): List<String> {
        val saved = prefs.getString("home", null)
        if (saved != null) return saved.split(",").filter { it.isNotEmpty() }
        val dockSet = dockPackages().toSet()
        val preferred = listOf(
            "com.android.chrome",
            "com.google.android.youtube",
            "com.android.vending",
            "com.google.android.apps.photos",
            "com.whatsapp",
            "com.instagram.android",
            "org.telegram.messenger",
            "com.google.android.gm",
            "com.google.android.apps.maps",
            "com.spotify.music"
        )
        val out = linkedSetOf<String>()
        out.add(SETTINGS_PKG)
        for (p in preferred) {
            if (allApps.any { it.pkg == p } && !dockSet.contains(p)) out.add(p)
        }
        for (a in allApps) {
            if (out.size >= 8) break
            if (!dockSet.contains(a.pkg)) out.add(a.pkg)
        }
        val list = out.toList()
        prefs.edit().putString("home", list.joinToString(",")).apply()
        return list
    }

    private fun refreshHome() {
        val homeList = homePackages().mapNotNull { p -> apps.firstOrNull { it.pkg == p } }
        pager.setContent(homeList, settings.columns, iconPx() + dp(38)) { a -> homeCell(a) }
    }

    private fun homeCell(a: AppEntry): View {
        val cell = appCell(a)
        cell.setOnClickListener { launch(a) }
        cell.setOnLongClickListener {
            showAppMenu(a)
            true
        }
        return cell
    }

    private fun toggleHome(a: AppEntry) {
        val list = homePackages().toMutableList()
        if (list.remove(a.pkg)) {
            Toast.makeText(this, "${a.label} removed from home", Toast.LENGTH_SHORT).show()
        } else {
            list.add(a.pkg)
               Toast.makeText(this, "${a.label} added to home", Toast.LENGTH_SHORT).show()
        }
        prefs.edit().putString("home", list.joinToString(",")).apply()
        refreshHome()
    }

    private fun updateDots(cur: Int, total: Int) {
        dots.removeAllViews()
        if (total <= 1) return
        for (i in 0 until total) {
            val d = View(this)
            d.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (i == cur) Color.WHITE else 0x66FFFFFF)
            }
            dots.addView(d, LinearLayout.LayoutParams(dp(6), dp(6)).apply {
                setMargins(dp(4), 0, dp(4), 0)
            })
        }
    }

    /* ---------------- dock ---------------- */

    private fun defaultDock(): List<String> {
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        )
        val pkgs = linkedSetOf<String>()
        for (i in intents) {
            val p = packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
            if (p != null && p != "android" && allApps.any { it.pkg == p }) pkgs.add(p)
        }
        for (a in allApps) {
            if (pkgs.size >= 3) break
            if (a.pkg != SETTINGS_PKG) pkgs.add(a.pkg)
        }
        return pkgs.toList().take(3)
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
        val entries = dockPackages().take(3).mapNotNull { p -> allApps.firstOrNull { it.pkg == p } }
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
        // App drawer button
        val drawerCell = FrameLayout(this)
        val drawerBtn = DrawerButtonView(this) { settings.circle }
        drawerBtn.setOnClickListener { drawer.show() }
        drawerCell.addView(drawerBtn, FrameLayout.LayoutParams(iconPx(), iconPx(), Gravity.CENTER))
        dock.addView(drawerCell, LinearLayout.LayoutParams(0, WRAP, 1f))
    }

    private fun toggleDock(pkg: String, label: String) {
        if (pkg == SETTINGS_PKG) return
        val list = dockPackages().take(3).toMutableList()
        if (list.remove(pkg)) {
            Toast.makeText(this, "$label removed from dock", Toast.LENGTH_SHORT).show()
        } else {
            list.add(pkg)
            while (list.size > 3) list.removeAt(0)
            Toast.makeText(this, "$label added to dock", Toast.LENGTH_SHORT).show()
        }
        prefs.edit().putString("dock", list.joinToString(",")).apply()
        refreshDock()
    }
}
