package com.android.notify.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.android.notify.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 通知被划掉时的恢复接收器
 *
 * 防删除保护第二层：当固定通知被用户划掉时，
 * 通过 deleteIntent 触发此接收器，立即恢复通知。
 *
 * 创建日期：2026-05-14
 * 作者：Cline
 */
class NotificationDismissReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notificationBarId = intent.getIntExtra(
            NotificationHelper.EXTRA_NOTIFICATION_BAR_ID, -1
        )
        if (notificationBarId == -1) return

        // 在IO线程恢复通知
        CoroutineScope(Dispatchers.IO).launch {
            NotificationHelper.restoreNotification(context, notificationBarId)
        }
    }
}
