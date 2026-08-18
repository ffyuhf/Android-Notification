package com.android.notify.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.android.notify.MainActivity
import com.android.notify.NotifyApp
import com.android.notify.R
import com.android.notify.util.NotificationHelper
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.android.notify.util.AppLogger

/**
 * 通知保活前台服务
 *
 * 职责：
 * 1. 保持应用进程存活，防止通知被系统回收
 * 2. 定期巡检通知栏，恢复被意外删除的固定通知（差异恢复）
 * 3. 开机自启后自动恢复所有固定通知
 *
 * 修复（2026-08-16）：
 * - B2 服务无法停止：原 onDestroy 无条件重启，ACTION_STOP 后服务死循环重启；
 *   现引入用户停止标志，仅系统回收场景尝试自愈，且 Android 12+ 后台启动
 *   受限时交由 START_STICKY 兜底重建
 * - P1 巡检差异恢复：巡检周期仅重发通知栏中缺失的通知，不再全量重发
 * - P3 onDestroy 取消协程作用域，修复泄漏；循环增加 isActive 退出条件
 * - P8 targetSdk 34+ 通过 ServiceCompat 显式指定前台服务类型
 * - 修正（2026-08-16 15:39 | 图片通知闪退修复）：serviceScope 追加
 *   CoroutineExceptionHandler——原作用域内未捕获异常（如图片解码 OOM Error）
 *   直接击穿进程导致「启动即闪退」且每次启动必现；现记 ERROR 日志后进程存活，
 *   单条失败由 NotificationHelper 恢复循环的 runCatching 二级隔离兜底
 * - 修正（2026-08-18 20:46 | 通知图标全白修复）：服务通知追加 setLargeIcon
 *   品牌图标，与 sendNotification 展示保持一致
 *
 * 创建日期：2026-05-14 | 作者：Cline
 */
class NotifyForegroundService : Service() {

    companion object {
        /** 巡检间隔：30秒 */
        private const val CHECK_INTERVAL_MS = 30_000L

        /** 日志标签（AppLogger 埋点统一使用） */
        private const val TAG = "NotifyFgService"

        /** Action：启动服务并恢复所有通知 */
        const val ACTION_START = "com.android.notify.ACTION_START_SERVICE"

        /** Action：停止服务 */
        const val ACTION_STOP = "com.android.notify.ACTION_STOP_SERVICE"

        /**
         * 用户主动停止标志（B2）
         *
         * true 表示停止由用户主动发起，onDestroy 不自动重启服务，
         * 避免"停止即重启"死循环与 Android 12+ 后台启动前台服务异常。
         * 显式调用 start() 时复位，保证再次发送通知后服务可正常恢复。
         */
        @Volatile
        private var isUserStopped = false

        /**
         * 启动前台服务
         *
         * @param context 上下文
         */
        fun start(context: Context) {
            // 显式启动视为需要服务运行，复位用户停止标志（B2）
            isUserStopped = false
            val intent = Intent(context, NotifyForegroundService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        /**
         * 停止前台服务
         *
         * @param context 上下文
         */
        fun stop(context: Context) {
            // 标记用户主动停止，阻止 onDestroy 自动重启（B2）
            isUserStopped = true
            val intent = Intent(context, NotifyForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    /**
     * 协程作用域，用于巡检任务
     *
     * 追加 CoroutineExceptionHandler（2026-08-16 15:39 | 图片通知闪退修复）：
     * 巡检/恢复链路任何未捕获异常仅记日志，不再崩溃进程。
     */
    private val serviceScope = CoroutineScope(
        Dispatchers.IO + Job() + CoroutineExceptionHandler { _, throwable ->
            AppLogger.e(TAG, "巡检/恢复协程未捕获异常", throwable)
        }
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForegroundWithServiceType()
                restoreAndStartCheckLoop()
            }
        }
        return START_STICKY
    }

    /**
     * 启动前台通知
     *
     * 前台服务必须显示一个通知。
     * 优化（2026-08-16 | P8）：targetSdk 34+ 需显式指定前台服务类型，
     * 使用 ServiceCompat 统一处理版本分支，避免类型缺失异常。
     */
    private fun startForegroundWithServiceType() {
        val notification = createServiceNotification()
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NotifyApp.FOREGROUND_SERVICE_NOTIFICATION_ID,
            notification,
            serviceType
        )
    }

    /**
     * 创建前台服务通知
     *
     * @return 低优先级通知，不打扰用户
     */
    private fun createServiceNotification(): Notification {
        val contentIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NotifyApp.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            // 通知头部品牌图标（修正 2026-08-18 20:46 | 通知图标全白修复）：
            // 与 sendNotification 一致，复用 NotificationHelper 的图标位图渲染
            .setLargeIcon(NotificationHelper.getAppIconBitmap(this))
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_service_channel_desc))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    /**
     * 恢复所有通知并启动巡检循环
     */
    private fun restoreAndStartCheckLoop() {
        serviceScope.launch {
            AppLogger.i(TAG, "前台服务启动，开始全量恢复固定通知")
            // 启动时全量恢复所有固定通知（服务启动/开机自启场景）
            NotificationHelper.restorePinnedNotifications(this@NotifyForegroundService, restoreAll = true)
            // 启动定期巡检
            startCheckLoop()
        }
    }

    /**
     * 定期巡检通知栏状态
     *
     * 优化（2026-08-16 | P1）：差异恢复。原实现每周期全量重发所有固定通知，
     * 造成重复提醒与耗电；现对比数据库记录与通知栏实际状态，仅恢复缺失的通知。
     * 优化（2026-08-16 | P3）：循环体感知协程取消，作用域取消时立即退出。
     */
    private suspend fun startCheckLoop() {
        while (currentCoroutineContext().isActive) {
            delay(CHECK_INTERVAL_MS)
            try {
                NotificationHelper.restorePinnedNotifications(this, restoreAll = false)
            } catch (_: Exception) {
                // 巡检异常不中断循环
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 取消巡检协程，避免作用域泄漏（P3）
        serviceScope.cancel()
        // 仅系统回收场景尝试自愈重启（B2）：
        // Android 12+ 系统杀死后从后台启动前台服务可能受限，
        // 失败时交由 START_STICKY 由系统择机重建
        if (!isUserStopped) {
            try {
                start(this)
            } catch (_: Exception) {
                // 后台启动前台服务受限，等待系统 START_STICKY 重建
            }
        }
    }
}
