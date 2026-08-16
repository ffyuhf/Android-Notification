package com.android.notify.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.android.notify.NotifyApp
import com.android.notify.R
import com.android.notify.data.datastore.NotificationSettingsSnapshot
import com.android.notify.data.datastore.SettingsDataStore
import com.android.notify.data.db.entity.NotificationEntity
import com.android.notify.data.repository.NotificationRepository
import com.android.notify.receiver.NotificationActionReceiver
import com.android.notify.receiver.NotificationDismissReceiver

/**
 * 通知工具类
 *
 * 封装通知的创建、更新、删除、恢复操作。
 * 所有通知栏交互均通过此工具类统一管理。
 *
 * 修复：sendNotification 改为 suspend 函数，移除 runBlocking 避免主线程阻塞导致闪退。
 * 优化（2026-08-16）：
 * - P1 巡检差异恢复：对比通知栏实际状态，仅重发缺失的固定通知
 * - P2 设置快照：批量发送复用单次 DataStore 读取结果
 * - P5 setOnlyAlertOnce：通知更新时不再重复响铃震动
 * - P6 通知栏ID唯一性校验：消除ID碰撞导致的通知互相覆盖
 *
 * 创建日期：2026-05-14 | 作者：Cline
 */
object NotificationHelper {

    // ===== 通知 Action 常量 =====

    /** 复制操作 Action */
    const val ACTION_COPY = "com.android.notify.ACTION_COPY"

    /**
     * 编辑操作 Action
     *
     * 改造（2026-08-16 | 通知栏内联编辑）：原为打开应用内编辑页（PendingIntent.getActivity），
     * 现改为带 RemoteInput 的广播内联编辑（Shizuku 同款 Direct Reply 交互）。
     */
    const val ACTION_EDIT = "com.android.notify.ACTION_EDIT"

    /** 内联编辑回复 Action（RemoteInput 结果回传） */
    const val ACTION_EDIT_REPLY = "com.android.notify.ACTION_EDIT_REPLY"

    /** RemoteInput 键：内联编辑的正文输入框 */
    const val KEY_EDIT_CONTENT = "edit_content"

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
     * 包含三层防删除保护：setOngoing + deleteIntent + 巡检差异恢复。
     *
     * 优化（2026-08-16 | P2）：settingsSnapshot 由调用方传入复用，
     * 批量发送时整批仅读取一次 DataStore；传 null 时内部自行读取一次。
     * 优化（2026-08-16 | P5）：setOnlyAlertOnce，通知更新不重复响铃震动。
     *
     * @param context 上下文
     * @param entity 通知实体
     * @param settingsSnapshot 设置快照（可选，批量场景复用）
     * @param soundEnabled 是否响铃提醒（默认true；B10 重发场景按用户设置决定）
     */
    suspend fun sendNotification(
        context: Context,
        entity: NotificationEntity,
        settingsSnapshot: NotificationSettingsSnapshot? = null,
        soundEnabled: Boolean = true
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 读取设置：优先使用调用方传入的快照，避免重复 DataStore IO（P2）
        val snapshot = settingsSnapshot ?: SettingsDataStore(context).getSnapshot()

        // 纯图通知（content 为空串）折叠视图/回退文本统一显示占位文案「[图片]」
        val displayContent = entity.content.ifBlank {
            context.getString(R.string.image_only_notification)
        }

        val builder = NotificationCompat.Builder(context, NotifyApp.CHANNEL_PINNED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(entity.title ?: context.getString(R.string.app_name))
            .setContentText(displayContent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // 重发场景按用户设置决定是否响铃提醒（B10/D3）；其余发送默认响铃
            .setDefaults(if (soundEnabled) NotificationCompat.DEFAULT_ALL else 0)
            // 更新已存在的通知时不再响铃震动，仅首次发送提醒（P5）
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            // 第一层防护：设置通知为持续性（不可滑动删除）
            .setOngoing(entity.isPinned)

        // 通知样式（新增 2026-08-16 | 图片通知）：
        // 有图且解码成功 → BigPictureStyle 大图优先（不受 multilineDisplay 限制）；
        // 无图或解码失败 → 回退原 BigTextStyle/默认样式（内存受控解码防 OOM）
        val imageBitmap = entity.imagePath?.let { ImageStorageHelper.decodeSampledBitmap(it) }
        if (imageBitmap != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(imageBitmap)
                    .setBigContentTitle(entity.title ?: context.getString(R.string.app_name))
                    .setSummaryText(displayContent)
            )
        } else if (snapshot.multilineDisplay) {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(displayContent)
                    .setBigContentTitle(entity.title ?: context.getString(R.string.app_name))
            )
        }

        // 第二层防护：防删除保护 - 设置 deleteIntent
        if (entity.isPinned && entity.isAntiDeleteEnabled && snapshot.antiDeleteProtection) {
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

        // 复制按钮（纯图通知 content 为空无内容可复制，不添加）
        if (snapshot.showCopyButton && entity.content.isNotBlank()) {
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

        // 编辑按钮（改造 2026-08-16 | 通知栏内联编辑）：
        // 由 PendingIntent.getActivity 打开应用内编辑页，改为带 RemoteInput 的广播，
        // 点击后在系统内联输入框直接编辑正文（Shizuku 同款交互）。
        // requestCode 契约保持不变（notificationId*10+2）。
        if (snapshot.showEditButton) {
            val remoteInput = RemoteInput.Builder(KEY_EDIT_CONTENT)
                .setLabel(context.getString(R.string.edit_reply_label))
                .build()
            val editIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_EDIT_REPLY
                putExtra(EXTRA_NOTIFICATION_ID, entity.id)
                putExtra(EXTRA_NOTIFICATION_BAR_ID, entity.notificationId)
            }
            val editPendingIntent = PendingIntent.getBroadcast(
                context,
                entity.notificationId * 10 + 2,
                editIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val editAction = NotificationCompat.Action.Builder(
                R.drawable.ic_edit,
                context.getString(R.string.action_edit),
                editPendingIntent
            )
                .addRemoteInput(remoteInput)
                .build()
            builder.addAction(editAction)
        }

        // 取消固定按钮
        if (snapshot.showUnpinButton && entity.isPinned) {
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
     * 用于防删除保护的第二层（deleteIntent触发）。
     *
     * @param context 上下文
     * @param notificationId 通知栏ID
     */
    suspend fun restoreNotification(context: Context, notificationId: Int) {
        val repository = NotificationRepository.getInstance(context)
        val entity = repository.getByNotificationId(notificationId) ?: return

        // 仅恢复活跃且固定且启用防删除的通知
        if (entity.isActive && entity.isPinned && entity.isAntiDeleteEnabled) {
            sendNotification(context, entity)
        }
    }

    /**
     * 恢复固定通知（巡检差异恢复）
     *
     * 优化（2026-08-16 | P1）：
     * 原实现对所有固定通知无条件全量重发，与架构文档"对比缺失恢复"不符，
     * 且造成重复提醒与耗电。现对比通知栏实际活跃通知，仅恢复缺失的。
     *
     * @param context 上下文
     * @param restoreAll true 全量恢复（服务启动/开机首次恢复）；
     *                   false 差异恢复（巡检周期，仅重发通知栏中缺失的）
     */
    suspend fun restorePinnedNotifications(context: Context, restoreAll: Boolean = false) {
        val repository = NotificationRepository.getInstance(context)
        // 整批复用同一份设置快照，仅读取一次 DataStore（P2）
        val snapshot = SettingsDataStore(context).getSnapshot()
        val activePinned = repository.getActivePinnedNotifications()

        // 差异恢复：过滤出通知栏中已缺失的固定通知
        val entitiesToRestore = if (restoreAll) {
            activePinned
        } else {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val activeBarIds = notificationManager.activeNotifications.map { it.id }.toHashSet()
            activePinned.filter { it.notificationId !in activeBarIds }
        }

        for (entity in entitiesToRestore) {
            sendNotification(context, entity, snapshot)
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
     * 生成不重复的通知栏ID
     *
     * 优化（2026-08-16 | P6）：原实现 1000 + count + (now % 10000) 存在碰撞可能，
     * 碰撞会导致通知互相覆盖。现改用时间戳截断 + 数据库唯一性校验，冲突时递增重试。
     *
     * @param context 上下文
     * @return 唯一的通知栏ID
     */
    suspend fun generateNotificationId(context: Context): Int {
        val repository = NotificationRepository.getInstance(context)
        // 时间戳低31位保证数值随机分散，校验保证与库内既有通知不冲突
        var candidate = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
        while (repository.existsByNotificationId(candidate)) {
            candidate += 1
        }
        return candidate
    }
}
