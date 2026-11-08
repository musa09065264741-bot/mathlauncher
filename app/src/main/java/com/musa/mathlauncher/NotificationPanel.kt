package com.musa.mathlauncher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val NP_MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val NP_WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

class NotificationPanel(context: Context) : FrameLayout(context) {

    private val accent = 0xFFFF9F0A.toInt()
    private val column = LinearLayout(context)
    private val clearBtn = TextView(context)
    private val accessBtn = TextView(context)
    private val emptyView = TextView(context)
    private val list = LinearLayout(context)
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    private fun px(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    init {
        visibility = View.GONE
        isClickable = true
        background = GradientDrawable().apply { setColor(0xF2080818.toInt()) }

        column.orientation = LinearLayout.VERTICAL

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(context).apply {
            text = "Notification Centre"
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        header.addView(title, LinearLayout.LayoutParams(0, NP_WRAP, 1f))

        clearBtn.apply {
            text = "Clear"
            textSize = 15f
            setTextColor(accent)
            setPadding(px(12), px(8), px(4), px(8))
            setOnClickListener { NotificationStore.service?.cancelAllNotifications() }
        }
        header.addView(clearBtn, LinearLayout.LayoutParams(NP_WRAP, NP_WRAP))
        column.addView(header, LinearLayout.LayoutParams(NP_MATCH, NP_WRAP).apply {
            bottomMargin = px(12)
        })

        accessBtn.apply {
            text = "Tap to allow notification access"
            textSize = 15f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(px(16), px(14), px(16), px(14))
            background = GradientDrawable().apply {
                cornerRadius = px(22).toFloat()
                setColor(accent)
            }
            setOnClickListener {
                val i = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(i)
                } catch (e: Exception) {
                    // settings screen not available
                }
            }
        }
        column.addView(accessBtn, LinearLayout.LayoutParams(NP_MATCH, NP_WRAP).apply {
            bottomMargin = px(12)
        })

        emptyView.apply {
            text = "No notifications"
            textSize = 16f
            setTextColor(0x99FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, px(60), 0, 0)
        }
        column.addView(emptyView, LinearLayout.LayoutParams(NP_MATCH, NP_WRAP))

        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        list.orientation = LinearLayout.VERTICAL
        scroll.addView(list, ViewGroup.LayoutParams(NP_MATCH, NP_WRAP))
        column.addView(scroll, LinearLayout.LayoutParams(NP_MATCH, 0, 1f))

        val handle = FrameLayout(context)
        val pill = View(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = px(3).toFloat()
                setColor(0x66FFFFFF)
            }
        }
        handle.addView(pill, FrameLayout.LayoutParams(px(44), px(5), Gravity.CENTER))
        handle.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_UP) hide()
            true
        }
        column.addView(handle, LinearLayout.LayoutParams(NP_MATCH, px(36)))

        addView(column, FrameLayout.LayoutParams(NP_MATCH, NP_MATCH))

        NotificationStore.onChange = { render() }
    }

    fun setInsets(top: Int, bottom: Int) {
        column.setPadding(px(16), top + px(12), px(16), bottom + px(8))
    }

    private fun slideDistance(): Float {
        val h = if (height > 0) height else resources.displayMetrics.heightPixels
        return h.toFloat()
    }

    fun show() {
        render()
        animate().cancel()
        visibility = View.VISIBLE
        translationY = -slideDistance()
        animate().translationY(0f).setDuration(250).start()
    }

    fun hide() {
        animate().cancel()
        animate().translationY(-slideDistance()).setDuration(220).withEndAction {
            visibility = View.GONE
        }.start()
    }

    fun render() {
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
        val items = NotificationStore.items.toList()

        accessBtn.visibility = if (enabled) View.GONE else View.VISIBLE
        clearBtn.visibility = if (enabled && items.isNotEmpty()) View.VISIBLE else View.INVISIBLE
        emptyView.visibility = if (enabled && items.isEmpty()) View.VISIBLE else View.GONE

        list.removeAllViews()
        for (n in items) {
            list.addView(row(n), LinearLayout.LayoutParams(NP_MATCH, NP_WRAP).apply {
                bottomMargin = px(10)
            })
        }
    }

    private fun row(n: NoteItem): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(px(14), px(12), px(10), px(12))
            background = GradientDrawable().apply {
                cornerRadius = px(22).toFloat()
                setColor(0x26FFFFFF)
            }
        }

        val icon = ImageView(context).apply {
            setImageDrawable(n.icon)
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, view.width * 0.225f)
                }
            }
        }
        card.addView(icon, LinearLayout.LayoutParams(px(38), px(38)))

        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        val top = TextView(context).apply {
            text = n.appName.uppercase() + "  ·  " + timeFmt.format(Date(n.time))
            textSize = 11f
            setTextColor(0x99FFFFFF.toInt())
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        col.addView(top)

        if (n.title.isNotEmpty()) {
            val t = TextView(context).apply {
                text = n.title
                textSize = 15f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            col.addView(t)
        }

        if (n.text.isNotEmpty()) {
            val b = TextView(context).apply {
                text = n.text
                textSize = 14f
                setTextColor(0xCCFFFFFF.toInt())
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
            }
            col.addView(b)
        }

        card.addView(col, LinearLayout.LayoutParams(0, NP_WRAP, 1f).apply {
            leftMargin = px(12)
        })

        val x = TextView(context).apply {
            text = "✕"
            textSize = 16f
            setTextColor(0x99FFFFFF.toInt())
            setPadding(px(12), px(4), px(8), px(4))
            setOnClickListener { NotificationStore.service?.cancelNotification(n.key) }
        }
        card.addView(x, LinearLayout.LayoutParams(NP_WRAP, NP_WRAP))

        card.setOnClickListener {
            try {
                n.intent?.send()
            } catch (e: Exception) {
                // notification can no longer be opened
            }
            hide()
        }
        return card
    }
}
