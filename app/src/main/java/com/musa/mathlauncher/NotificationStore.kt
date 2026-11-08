package com.musa.mathlauncher

import android.app.PendingIntent
import android.graphics.drawable.Drawable

class NoteItem(
    val key: String,
    val appName: String,
    val title: String,
    val text: String,
    val time: Long,
    val icon: Drawable?,
    val intent: PendingIntent?
)

object NotificationStore {
    val items = mutableListOf<NoteItem>()
    var onChange: (() -> Unit)? = null
    var service: NotificationService? = null
}
