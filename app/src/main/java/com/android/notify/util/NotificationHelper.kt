package com.android.notify.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.android.notify.EditNotificationActivity
import com.android.notify.NotifyApp
import com.android.notify.R
import com.android.notify.data.datastore.SettingsDataStore
import com.android.notify.data.db.entity.NotificationEntity
import com.android.notify.receiver.NotificationActionReceiver
import com.android.notify.receiver.NotificationDismissReceiver
import kotlinx.coroutines.flow.first

/**
 * 通知工具类
 *
 * 封装通知的创建、更新、删除、恢复操作。
 * 所有通知栏交互均通过此工具类统一管理。
 *
 * 修复：sendNotification 改为 suspend 函数，移除 runBlocking 避免主线程阻塞导致闪退。
 *
 * 创建日期：2026-05-14
 * 作者：Cline
 */
object NotificationHelper {

    // ===== 通知 Action 常量 =====

    /** 复制操作 Action */
    const val ACTION_COPY = "com.android.notify.ACTION_COPY"

    /** 编辑操作 Action */
    const val ACTION_EDIT = "com.android.notify.ACTION_EDIT"

    /** 取消固定操作 Action */
    const val ACTION_UNPIN = "com.android.notify.ACTION_UNPIN"

    /** Intent Extra：通知记录数据库ID */
    const val EXTRA_NOTIFICATION_ID = "notification_id"

    /** Intent Extra：通知栏ID */
    const val EXTRA_NOTIFICATION_BAR_ID = "notification_bar_id"

    /** Intent Extra：通知内容（用于复制） */
    const val EXTRA_NOTIFICATION_CONTENT = "notification_content"

    /**
     * 发送通知到通知栏（挂起函数）
     *
     * 根据通知实体和当前设置构建并显示通知。
     * 包含三层防删除保护：setOngoing + deleteIntent + 巡检恢复。
     *
     * 修复：改为 suspend 函数，移除 runBlocking，避免主线程阻塞导致闪退。
     *
     * @param context 上下文
     * @param entity 通知实体
     * @param settings DataStore设置（读取按钮开关）
     */
    suspend fun sendNotification(
        context: Context,
        entity: NotificationEntity,
        settings: SettingsDataStore? = null
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 读取设置（挂起读取，不再阻塞主线程）
        val showCopy = settings?.showCopyButton?.first() ?: true
        val showEdit = settings?.showEditButton?.first() ?: true
        val showUnpin = settings?.showUnpinButton?.first() ?: true
        val multiline = settings?.multilineDisplay?.first() ?: true
        val antiDelete = settings?.antiDeleteProtection?.first() ?: true

        val builder = NotificationCompat.Builder(context, NotifyApp.CHANNEL_PINNED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(entity.title ?: context.getString(R.string.app_name))
            .setContentText(entity.content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(false)
            // 第一层防护：设置通知为持续性（不可滑动删除）
            .setOngoing(entity.isPinned)

        // 多行显示样式
        if (multiline) {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(entity.content)
                    .setBigContentTitle(entity.title ?: context.getString(R.string.app_name))
            )
        }

        // 第二层防护：防删除保护 - 设置 deleteIntent
        if (entity.isPinned && entity.isAntiDeleteEnabled && antiDelete) {
            val dismissIntent = Intent(context, NotificationDismissReceiver::class.java).apply {
                putExtra(EXTRA_NOTIFICATION_ID, entity.id)
                putExtra(EXTRA_NOTIFICATION_BAR_ID, entity.notificationId)
                action = "DISMISS_${entity.notificationId}"
            }
            val dismissPendingIntent = PendingIntent.getBroadcast(
                context,
                entity.notificationId,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setDeleteIntent(dismissPendingIntent)
        }

        // 复制按钮
        if (showCopy) {
            val copyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_COPY
                putExtra(EXTRA_NOTIFICATION_ID, entity.id)
                putExtra(EXTRA_NOTIFICATION_CONTENT, entity.content)
            }
            val copyPendingIntent = PendingIntent.getBroadcast(
                context,
                entity.notificationId * 10 + 1,
                copyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_copy,
                context.getString(R.string.action_copy),
                copyPendingIntent
            )
        }

        // 编辑按钮（添加唯一 action 确保 PendingIntent 不被系统合并）
        if (showEdit) {
            val editIntent = Intent(context, EditNotificationActivity::class.java).apply {
                action = "EDIT_${entity.id}"
                putExtra(EXTRA_NOTIFICATION_ID, entity.id)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val editPendingIntent = PendingIntent.getActivity(
                context,
                entity.notificationId * 10 + 2,
                editIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_edit,
                context.getString(R.string.action_edit),
                editPendingIntent
            )
        }

        // 取消固定按钮
        if (showUnpin && entity.isPinned) {
            val unpinIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_UNPIN
                putExtra(EXTRA_NOTIFICATION_ID, entity.id)
                putExtra(EXTRA_NOTIFICATION_BAR_ID, entity.notificationId)
            }
            val unpinPendingIntent = PendingIntent.getBroadcast(
                context,
                entity.notificationId * 10 + 3,
                unpinIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_unpin,
                context.getString(R.string.action_unpin),
                unpinPendingIntent
            )
        }

        // 不设置 contentIntent：点击通知体无任何响应，仅按钮可操作

        notificationManager.notify(entity.notificationId, builder.build())
    }

    /**
     * 恢复被删除的通知
     *
     * 从数据库读取通知数据并重新发送到通知栏。
     * 用于防删除保护的第二层（deleteIntent触发）和第三层（巡检触发）。
     *
     * @param context 上下文
     * @param notificationId 通知栏ID
     */
    suspend fun restoreNotification(context: Context, notificationId: Int) {
        val repository = com.android.notify.data.repository.NotificationRepository.getInstance(context)
        val entity = repository.getByNotificationId(notificationId) ?: return

        // 仅恢复活跃且固定且启用防删除的通知
        if (entity.isActive && entity.isPinned && entity.isAntiDeleteEnabled) {
            val settings = SettingsDataStore(context)
            sendNotification(context, entity, settings)
        }
    }

    /**
     * 按通知栏ID取消通知
     *
     * @param context 上下文
     * @param notificationId 通知栏ID
     */
    fun cancelNotification(context: Context, notificationId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }

    /**
     * 恢复所有活跃的固定通知
     *
     * 用于开机自启和前台服务启动时批量恢复。
     *
     * @param context 上下文
     */
    suspend fun restoreAllPinnedNotifications(context: Context) {
        val repository = com.android.notify.data.repository.NotificationRepository.getInstance(context)
        val settings = SettingsDataStore(context)
        val activePinned = repository.getActivePinnedNotifications()

        for (entity in activePinned) {
            sendNotification(context, entity, settings)
        }
    }

    /**
     * 生成不重复的通知栏ID
     *
     * @param context 上下文
     * @return 唯一的通知栏ID
     */
    suspend fun generateNotificationId(context: Context): Int {
        val repository = com.android.notify.data.repository.NotificationRepository.getInstance(context)
        val count = repository.getCount()
        // 从1000开始，避免与系统通知ID冲突
        return 1000 + count + (System.currentTimeMillis() % 10000).toInt()
    }
}
