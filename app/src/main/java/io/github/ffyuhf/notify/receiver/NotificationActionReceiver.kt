package io.github.ffyuhf.notify.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.RemoteInput
import io.github.ffyuhf.notify.R
import io.github.ffyuhf.notify.data.datastore.SettingsDataStore
import io.github.ffyuhf.notify.data.repository.NotificationRepository
import io.github.ffyuhf.notify.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 通知栏操作按钮接收器
 *
 * 处理通知栏上的操作按钮点击：
 * - ACTION_COPY：复制通知内容到剪贴板
 * - ACTION_UNPIN：取消固定通知
 * - ACTION_EDIT_REPLY：通知栏内联编辑（RemoteInput）结果回传
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotificationHelper.ACTION_COPY -> handleCopy(context, intent)
            NotificationHelper.ACTION_UNPIN -> handleUnpin(context, intent)
            NotificationHelper.ACTION_EDIT_REPLY -> handleEditReply(context, intent)
        }
    }

    /**
     * 处理通知栏内联编辑回复
     *
     * 用户在内联输入框中提交新正文后：
     * 1. RemoteInput.getResultsFromIntent 取回用户输入的文本
     * 2. 更新数据库正文（标题保持不变）
     * 3. 重发通知刷新内容（原生契约：回复后必须重发通知，否则内联框 spinner 永不消失）
     *
     * 数据库操作为异步，使用 goAsync() 延长 Receiver 生命周期。
     */
    private fun handleEditReply(context: Context, intent: Intent) {
        val newContent = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(NotificationHelper.KEY_EDIT_CONTENT)
            ?.toString()
            ?.trim()
            ?: return

        // 空输入直接忽略，不触发更新
        if (newContent.isEmpty()) return

        val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, -1)
        if (notificationId == -1) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = NotificationRepository.getInstance(context)
                val entity = repository.getById(notificationId) ?: return@launch

                // 仅更新正文，标题保持不变（产品决策：内联编辑不改标题）
                repository.updateContent(notificationId, entity.title, newContent)

                // 重发通知刷新内容并终止内联编辑 spinner
                val updatedEntity = entity.copy(content = newContent)
                NotificationHelper.sendNotification(
                    context,
                    updatedEntity,
                    SettingsDataStore(context).getSnapshot()
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * 处理复制操作
     *
     * 将通知内容复制到系统剪贴板，并显示Toast提示。
     */
    private fun handleCopy(context: Context, intent: Intent) {
        val content = intent.getStringExtra(NotificationHelper.EXTRA_NOTIFICATION_CONTENT) ?: return

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText(
            context.getString(R.string.app_name),
            content
        )
        clipboard.setPrimaryClip(clip)

        Toast.makeText(context, R.string.toast_copied, Toast.LENGTH_SHORT).show()
    }

    /**
     * 处理取消固定操作
     *
     * 将通知从固定状态变为非固定，允许用户滑动删除。
     * 同时更新数据库中的活跃状态（数据库操作异步，goAsync() 延长 Receiver 生命周期）。
     */
    private fun handleUnpin(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, -1)
        val notificationBarId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_BAR_ID, -1)

        if (notificationId == -1) return

        // goAsync 延长 Receiver 生命周期，确保异步操作在进程被回收前完成
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = NotificationRepository.getInstance(context)
                val entity = repository.getById(notificationId) ?: return@launch

                repository.deactivateById(notificationId)

                if (notificationBarId != -1) {
                    NotificationHelper.cancelNotification(context, notificationBarId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
