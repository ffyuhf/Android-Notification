package io.github.ffyuhf.notify.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import io.github.ffyuhf.notify.NotifyApp
import io.github.ffyuhf.notify.R
import io.github.ffyuhf.notify.data.datastore.NotificationSettingsSnapshot
import io.github.ffyuhf.notify.data.datastore.SettingsDataStore
import io.github.ffyuhf.notify.data.db.entity.NotificationEntity
import io.github.ffyuhf.notify.data.repository.NotificationRepository
import io.github.ffyuhf.notify.receiver.NotificationActionReceiver
import io.github.ffyuhf.notify.receiver.NotificationDismissReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 通知工具类
 *
 * 封装通知的创建、更新、删除、恢复操作。
 * 所有通知栏交互均通过此工具类统一管理。
 */
object NotificationHelper {

    // ===== 通知 Action 常量 =====

    /** 复制操作 Action */
    const val ACTION_COPY = "io.github.ffyuhf.notify.ACTION_COPY"

    /** 编辑操作 Action（触发通知栏 RemoteInput 内联编辑） */
    const val ACTION_EDIT = "io.github.ffyuhf.notify.ACTION_EDIT"

    /** 内联编辑回复 Action（RemoteInput 结果回传） */
    const val ACTION_EDIT_REPLY = "io.github.ffyuhf.notify.ACTION_EDIT_REPLY"

    /** RemoteInput 键：内联编辑的正文输入框 */
    const val KEY_EDIT_CONTENT = "edit_content"

    /** 取消固定操作 Action */
    const val ACTION_UNPIN = "io.github.ffyuhf.notify.ACTION_UNPIN"

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
     * 图片解码固定在 IO 线程执行；解码失败/OOM 回退文本样式并记 WARN 日志。
     *
     * @param context 上下文
     * @param entity 通知实体
     * @param settingsSnapshot 设置快照（可选，批量场景复用以减少 DataStore 读取次数）
     * @param soundEnabled 是否响铃提醒（默认 true；重发场景由用户设置决定）
     * @return true 发布成功；false 发布失败（已记 ERROR 日志，用户主动路径需向用户提示）
     */
    suspend fun sendNotification(
        context: Context,
        entity: NotificationEntity,
        settingsSnapshot: NotificationSettingsSnapshot? = null,
        soundEnabled: Boolean = true
    ): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val snapshot = settingsSnapshot ?: SettingsDataStore(context).getSnapshot()

        // 纯图通知（content 为空串）折叠视图/回退文本统一显示占位文案「[图片]」
        val displayContent = entity.content.ifBlank {
            context.getString(R.string.image_only_notification)
        }

        // 渠道按 soundEnabled 选择：Android 8.0+（minSdk=26）通知声音/震动由渠道决定，
        // setDefaults 无效；true → 响铃渠道（IMPORTANCE_HIGH），false → 静默渠道
        // （IMPORTANCE_LOW，无声音/震动/横幅，通知静默出现在通知栏）
        val channelId = if (soundEnabled) {
            NotifyApp.CHANNEL_PINNED
        } else {
            NotifyApp.CHANNEL_PINNED_SILENT
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            // 标题为 null 不渲染标题行：展开态应用名由 SystemUI 显示于通知头部
            .setContentTitle(entity.title)
            .setContentText(displayContent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // 更新已存在的通知时不再响铃震动，仅首次发送提醒
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            // 第一层防护：设置通知为持续性（不可滑动删除）
            .setOngoing(entity.isPinned)

        // 有图且解码成功 → BigPictureStyle 大图优先（不受 multilineDisplay 限制）；
        // 无图或解码失败 → 回退 BigTextStyle/默认样式（内存受控解码防 OOM）
        val imageBitmap = entity.imagePath?.let { path ->
            withContext(Dispatchers.IO) { ImageStorageHelper.decodeSampledBitmap(path) }
        }
        if (imageBitmap != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(imageBitmap)
                    // 大标题 null 不渲染，应用名由 SystemUI 头部显示
                    .setBigContentTitle(entity.title)
                    .setSummaryText(displayContent)
            )
        } else {
            if (entity.imagePath != null) {
                // 有图片路径但解码失败/文件缺失/OOM → 回退文本样式并记日志
                AppLogger.w(TAG, "通知 ${entity.notificationId} 图片解码失败，回退文本样式")
            }
            if (snapshot.multilineDisplay) {
                builder.setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(displayContent)
                        // 大标题 null 不渲染，应用名由 SystemUI 头部显示
                        .setBigContentTitle(entity.title)
                )
            }
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

        // 编辑按钮：带 RemoteInput 的广播，点击后在系统内联输入框直接编辑正文（Direct Reply）。
        // requestCode 契约：notificationId*10+2。
        if (snapshot.showEditButton) {
            val remoteInput = RemoteInput.Builder(KEY_EDIT_CONTENT)
                .setLabel(context.getString(R.string.edit_reply_label))
                .build()
            val editIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_EDIT_REPLY
                putExtra(EXTRA_NOTIFICATION_ID, entity.id)
                putExtra(EXTRA_NOTIFICATION_BAR_ID, entity.notificationId)
            }
            // 携带 RemoteInput 的 Action 其 PendingIntent 必须 FLAG_MUTABLE：
            // Android 12+ 系统触发时需向 Intent 回填用户输入，IMMUTABLE 会被系统以
            // "PendingIntents attached to actions with remote inputs must be mutable" 拒绝发布。
            val editPendingIntent = PendingIntent.getBroadcast(
                context,
                entity.notificationId * 10 + 2,
                editIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
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

        // notify 包 runCatching 并向上返回发布结果：非法通知配置会抛 IllegalArgumentException
        // 击穿主线程协程；失败记 ERROR 日志不外抛，由调用方按返回值决定是否向用户提示
        // （后台路径静默，用户主动路径 Toast）。
        val postResult = runCatching {
            notificationManager.notify(entity.notificationId, builder.build())
        }
        postResult.onSuccess {
            AppLogger.d(TAG, "通知已发送 barId=${entity.notificationId} 有图=${imageBitmap != null}")
        }.onFailure { throwable ->
            AppLogger.e(TAG, "通知发布失败 barId=${entity.notificationId}", throwable)
        }
        return postResult.isSuccess
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
     * 对比通知栏实际活跃通知，仅恢复缺失的（避免全量重发造成重复提醒与耗电）。
     * 恢复循环单条 runCatching 容错：单条异常（如图片 OOM）不中断整批、不击穿 serviceScope。
     *
     * @param context 上下文
     * @param restoreAll true 全量恢复（服务启动/开机首次恢复）；
     *                   false 差异恢复（巡检周期，仅重发通知栏中缺失的）
     */
    suspend fun restorePinnedNotifications(context: Context, restoreAll: Boolean = false) {
        val repository = NotificationRepository.getInstance(context)
        // 整批复用同一份设置快照，仅读取一次 DataStore
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
            // 单条容错：runCatching 捕获含 Error 在内的 Throwable，坏记录不拖垮整批
            runCatching { sendNotification(context, entity, snapshot) }
                .onFailure { throwable ->
                    // 直接透传 Throwable：OOM 等 Error 类型堆栈也需完整保留
                    AppLogger.e(
                        TAG,
                        "恢复通知失败 dbId=${entity.id} barId=${entity.notificationId}",
                        throwable
                    )
                }
        }
        AppLogger.i(TAG, "巡检恢复完成：待恢复 ${entitiesToRestore.size} 条（restoreAll=$restoreAll）")
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
     * 生成不重复的通知栏ID（碰撞会导致通知互相覆盖）
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

    /** 日志标签（AppLogger 埋点统一使用） */
    private const val TAG = "NotificationHelper"
}
