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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 * 修正（2026-08-16 18:02 | RemoteInput 可变性 + 发布容错）：
 * - 编辑 Action PendingIntent 改 FLAG_MUTABLE：Android 12+（API 31+）强制要求携带
 *   RemoteInput 的 Action 其 PendingIntent 必须可变，IMMUTABLE 会被系统拒绝发布
 *   （恢复失败与主线程崩溃循环的根因）
 * - notify() 包 runCatching 并返回 Boolean：发布失败记 ERROR 日志，不再击穿调用协程
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
     * 修正（2026-08-16 15:39 | 图片通知闪退修复）：
     * - 图片解码包 withContext(Dispatchers.IO)：原实现跟随调用方线程执行，
     *   viewModelScope（主线程）发送大图时在主线程做磁盘 IO+位图解码，
     *   为「发送图片后闪退」根因之三；现固定在 IO 线程解码
     * - 解码失败/OOM 回退文本样式时记 WARN 日志（供分级别导出排查）
     *
     * @param context 上下文
     * @param entity 通知实体
     * @param settingsSnapshot 设置快照（可选，批量场景复用）
     * @param soundEnabled 是否响铃提醒（默认true；B10 重发场景按用户设置决定）
     * @return true 发布成功；false 发布失败（已记 ERROR 日志，用户主动路径需向用户提示）
     */
    suspend fun sendNotification(
        context: Context,
        entity: NotificationEntity,
        settingsSnapshot: NotificationSettingsSnapshot? = null,
        soundEnabled: Boolean = true
    ): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 读取设置：优先使用调用方传入的快照，避免重复 DataStore IO（P2）
        val snapshot = settingsSnapshot ?: SettingsDataStore(context).getSnapshot()

        // 纯图通知（content 为空串）折叠视图/回退文本统一显示占位文案「[图片]」
        val displayContent = entity.content.ifBlank {
            context.getString(R.string.image_only_notification)
        }

        // 渠道按 soundEnabled 选择（修正 2026-08-16 | 重发响铃修复）：
        // Android 8.0+（minSdk=26）通知声音/震动由渠道决定，setDefaults 无效；
        // true → 响铃渠道（IMPORTANCE_HIGH），false → 静默渠道（IMPORTANCE_LOW，
        // 无声音/震动/横幅，通知静默出现在通知栏）
        val channelId = if (soundEnabled) {
            NotifyApp.CHANNEL_PINNED
        } else {
            NotifyApp.CHANNEL_PINNED_SILENT
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(entity.title ?: context.getString(R.string.app_name))
            .setContentText(displayContent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // 更新已存在的通知时不再响铃震动，仅首次发送提醒（P5）
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            // 第一层防护：设置通知为持续性（不可滑动删除）
            .setOngoing(entity.isPinned)

        // 通知样式（新增 2026-08-16 | 图片通知）：
        // 有图且解码成功 → BigPictureStyle 大图优先（不受 multilineDisplay 限制）；
        // 无图或解码失败 → 回退原 BigTextStyle/默认样式（内存受控解码防 OOM）
        // 修正（2026-08-16 15:39）：解码固定在 IO 线程执行，不再跟随调用方线程
        val imageBitmap = entity.imagePath?.let { path ->
            withContext(Dispatchers.IO) { ImageStorageHelper.decodeSampledBitmap(path) }
        }
        if (imageBitmap != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(imageBitmap)
                    .setBigContentTitle(entity.title ?: context.getString(R.string.app_name))
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
                        .setBigContentTitle(entity.title ?: context.getString(R.string.app_name))
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
            // 修正（2026-08-16 18:02 | RemoteInput 可变性）：FLAG_IMMUTABLE → FLAG_MUTABLE。
            // Android 12+ 强制要求携带 RemoteInput 的 Action 其 PendingIntent 必须可变
            // （系统触发时需向 Intent 回填用户输入），IMMUTABLE 会被系统以
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

        // 修正（2026-08-16 18:02 | 发布容错）：notify 包 runCatching 并向上返回发布结果。
        // 非法通知配置（如 IMMUTABLE RemoteInput Action）会抛 IllegalArgumentException
        // 击穿主线程协程导致应用崩溃循环；现失败记 ERROR 日志不外抛，
        // 由调用方按返回值决定是否向用户提示（后台路径静默，用户主动路径 Toast）。
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
     * 优化（2026-08-16 | P1）：
     * 原实现对所有固定通知无条件全量重发，与架构文档"对比缺失恢复"不符，
     * 且造成重复提醒与耗电。现对比通知栏实际活跃通知，仅恢复缺失的。
     *
     * @param context 上下文
     * @param restoreAll true 全量恢复（服务启动/开机首次恢复）；
     *                   false 差异恢复（巡检周期，仅重发通知栏中缺失的）
     * 修正（2026-08-16 15:39 | 图片通知闪退修复）：恢复循环单条容错——
     * 原实现单条异常（如图片 OOM）直接中断整批恢复并击穿 serviceScope 导致
     * 进程崩溃（「启动即闪退」根因）；现单条 runCatching 隔离，失败记日志继续。
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

    /** 日志标签（AppLogger 埋点统一使用） */
    private const val TAG = "NotificationHelper"
}
