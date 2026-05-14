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
 * 修复：使用 goAsync() 确保 BroadcastReceiver 能完成异步操作。
 * BroadcastReceiver 的 onReceive 默认在主线程执行且有时间限制，
 * 异步操作需要 goAsync() 延长生命周期。
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

        // 使用 goAsync() 延长 Receiver 生命周期，确保异步恢复操作能完成
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 立即恢复被划掉的通知
                NotificationHelper.restoreNotification(context, notificationBarId)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
