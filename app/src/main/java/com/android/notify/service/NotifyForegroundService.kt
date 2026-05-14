package com.android.notify.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.android.notify.MainActivity
import com.android.notify.NotifyApp
import com.android.notify.R
import com.android.notify.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 通知保活前台服务
 *
 * 职责：
 * 1. 保持应用进程存活，防止通知被系统回收
 * 2. 定期巡检通知栏，恢复被意外删除的固定通知
 * 3. 开机自启后自动恢复所有固定通知
 *
 * 创建日期：2026-05-14
 * 作者：Cline
 */
class NotifyForegroundService : Service() {

    companion object {
        /** 巡检间隔：30秒 */
        private const val CHECK_INTERVAL_MS = 30_000L

        /** Action：启动服务并恢复所有通知 */
        const val ACTION_START = "com.android.notify.ACTION_START_SERVICE"

        /** Action：停止服务 */
        const val ACTION_STOP = "com.android.notify.ACTION_STOP_SERVICE"

        /**
         * 启动前台服务
         *
         * @param context 上下文
         */
        fun start(context: Context) {
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
            val intent = Intent(context, NotifyForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    /** 协程作用域，用于巡检任务 */
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                startForeground()
                restoreAndStartCheckLoop()
            }
            else -> {
                startForeground()
                restoreAndStartCheckLoop()
            }
        }
        return START_STICKY
    }

    /**
     * 启动前台通知
     *
     * 前台服务必须显示一个通知。
     */
    private fun startForeground() {
        val notification = createServiceNotification()
        startForeground(NotifyApp.FOREGROUND_SERVICE_NOTIFICATION_ID, notification)
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
            // 启动时恢复所有固定通知
            NotificationHelper.restoreAllPinnedNotifications(this@NotifyForegroundService)
            // 启动定期巡检
            startCheckLoop()
        }
    }

    /**
     * 定期巡检通知栏状态
     *
     * 对比数据库记录与通知栏实际状态，
     * 恢复被意外删除的固定通知（第三层防护）。
     */
    private suspend fun startCheckLoop() {
        while (true) {
            delay(CHECK_INTERVAL_MS)
            try {
                NotificationHelper.restoreAllPinnedNotifications(this)
            } catch (_: Exception) {
                // 巡检异常不中断循环
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 服务被销毁时尝试重启
        start(this)
    }
}
