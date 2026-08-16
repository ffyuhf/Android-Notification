package com.android.notify.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.android.notify.R
import com.android.notify.data.datastore.SettingsDataStore
import com.android.notify.data.repository.NotificationRepository
import com.android.notify.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 通知栏操作按钮接收器
 *
 * 处理通知栏上的操作按钮点击：
 * - ACTION_COPY：复制通知内容到剪贴板
 * - ACTION_UNPIN：取消固定通知
 *
 * 修复（2026-08-16 | B7）：handleUnpin 的数据库操作改用 goAsync()
 * 延长 Receiver 生命周期，确保异步操作在进程被回收前完成。
 *
 * 创建日期：2026-05-14 | 作者：Cline
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotificationHelper.ACTION_COPY -> handleCopy(context, intent)
            NotificationHelper.ACTION_UNPIN -> handleUnpin(context, intent)
        }
    }

    /**
     * 处理复制操作
     *
     * 将通知内容复制到系统剪贴板，并显示Toast提示。
     */
    private fun handleCopy(context: Context, intent: Intent) {
        val content = intent.getStringExtra(NotificationHelper.EXTRA_NOTIFICATION_CONTENT) ?: return

        // 复制到剪贴板
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText(
            context.getString(R.string.app_name),
            content
        )
        clipboard.setPrimaryClip(clip)

        // 显示复制成功提示
        Toast.makeText(context, R.string.toast_copied, Toast.LENGTH_SHORT).show()
    }

    /**
     * 处理取消固定操作
     *
     * 将通知从固定状态变为非固定，允许用户滑动删除。
     * 同时更新数据库中的活跃状态。
     *
     * 修复（2026-08-16 | B7）：goAsync() 延长 Receiver 生命周期，
     * 确保数据库更新与通知取消在进程被回收前完成。
     */
    private fun handleUnpin(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, -1)
        val notificationBarId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_BAR_ID, -1)

        if (notificationId == -1) return

        // goAsync 延长 Receiver 生命周期，确保异步操作完成（B7）
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = NotificationRepository.getInstance(context)
                val entity = repository.getById(notificationId) ?: return@launch

                // 更新数据库：标记为非活跃
                repository.deactivateById(notificationId)

                // 取消通知栏显示
                if (notificationBarId != -1) {
                    NotificationHelper.cancelNotification(context, notificationBarId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
