package com.musa.mathlauncher

import android.app.Notification
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        NotificationStore.service = this
        refresh()
    }

    override fun onListenerDisconnected() {
        NotificationStore.service = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        refresh()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        refresh()
    }

    private fun refresh() {
        val pm = packageManager
        val active: Array<StatusBarNotification> = try {
            activeNotifications ?: emptyArray()
        } catch (e: Exception) {
            emptyArray()
        }
        val list = active
            .filter { it.packageName != packageName && !it.isOngoing }
            .filter { (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
            .mapNotNull { sbn ->
                val extras = sbn.notification.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                if (title.isEmpty() && text.isEmpty()) return@mapNotNull null
                val appName = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
                } catch (e: Exception) {
                    sbn.packageName
                }
                val icon = try {
                    pm.getApplicationIcon(sbn.packageName)
                } catch (e: Exception) {
                    null
                }
                NoteItem(sbn.key, appName, title, text, sbn.postTime, icon, sbn.notification.contentIntent)
            }
            .sortedByDescending { it.time }
        Handler(Looper.getMainLooper()).post {
            NotificationStore.items.clear()
            NotificationStore.items.addAll(list)
            NotificationStore.onChange?.invoke()
        }
    }
}
