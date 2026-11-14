package com.musa.mathlauncher

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout

/** Home screen pages: swipe left and right between pages of apps */
class HomePager(context: Context) : HorizontalScrollView(context) {

    private val strip = LinearLayout(context)
    private var items: List<AppEntry> = emptyList()
    private var columns = 4
    private var rowHeight = 0
    private var cellFactory: (AppEntry) -> View = { View(context) }
    private var pages = 1
    private var lastPage = -1
    private var startPage = 0
    private var startScroll = 0

    var onPage: (Int, Int) -> Unit = { _, _ -> }

    private fun px(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        strip.orientation = LinearLayout.HORIZONTAL
        addView(strip, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    fun setContent(list: List<AppEntry>, cols: Int, rowH: Int, factory: (AppEntry) -> View) {
        items = list
        columns = cols
        rowHeight = rowH
        cellFactory = factory
        rebuild()
    }

    fun goToFirstPage() {
        scrollTo(0, 0)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    private fun rebuild() {
        if (width <= 0 || height <= 0 || rowHeight <= 0) return
        val rows = ((height - px(8)) / rowHeight).coerceAtLeast(1)
        val perPage = rows * columns
        pages = ((items.size + perPage - 1) / perPage).coerceAtLeast(1)
        strip.removeAllViews()
        for (p in 0 until pages) {
            val page = LinearLayout(context)
            page.orientation = LinearLayout.VERTICAL
            page.setPadding(px(12), px(8), px(12), 0)
            for (r in 0 until rows) {
                val row = LinearLayout(context)
                row.orientation = LinearLayout.HORIZONTAL
                for (c in 0 until columns) {
                    val idx = p * perPage + r * columns + c
                    val cell = if (idx < items.size) cellFactory(items[idx]) else View(context)
                    row.addView(cell, LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                    ))
                }
                page.addView(row, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = px(18) })
            }
            strip.addView(page, LinearLayout.LayoutParams(
                width, ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
        if (scrollX > (pages - 1) * width) scrollTo((pages - 1) * width, 0)
        post { notifyPage(true) }
    }

    private fun currentPage(): Int {
        if (width <= 0) return 0
        return ((scrollX + width / 2) / width).coerceIn(0, pages - 1)
    }

    private fun notifyPage(force: Boolean) {
        val cur = currentPage()
        if (force || cur != lastPage) {
            lastPage = cur
            onPage(cur, pages)
        }
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        notifyPage(false)
    }

    // No coasting: always settle neatly on one page
    override fun fling(velocityX: Int) {
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            startScroll = scrollX
            startPage = currentPage()
        }
        val handled = super.dispatchTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP ||
            ev.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            snap()
        }
        return handled
    }

    private fun snap() {
        if (width <= 0) return
        val delta = scrollX - startScroll
        var target = startPage
        if (delta > width / 6) {
            target = startPage + 1
        } else if (delta < -width / 6) {
            target = startPage - 1
        }
        target = target.coerceIn(0, pages - 1)
        smoothScrollTo(target * width, 0)
    }
}
