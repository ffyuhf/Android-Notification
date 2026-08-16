package com.android.notify

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.android.notify.util.AppLogger

/**
 * 应用程序入口类
 *
 * 职责：
 * 1. 创建通知渠道（Android 8.0+ 必须）
 * 2. 初始化全局组件（日志、崩溃黑匣子）
 *
 * 创建日期：2026-05-14
 * 作者：Cline
 * 修改（2026-08-16 15:39 | 图片通知闪退修复与日志导出）：
 * - 接入 AppLogger：初始化上下文 + 启动容量清理
 * - 崩溃黑匣子：全局未捕获异常堆栈同步落盘日志文件（供设置页导出分析），
 *   记录完毕后交还系统默认处理器，保持原生崩溃行为不变
 */
class NotifyApp : Application() {

    companion object {
        /** 固定通知渠道ID */
        const val CHANNEL_PINNED = "channel_pinned"

        /** 前台服务通知渠道ID */
        const val CHANNEL_SERVICE = "channel_service"

        /** 前台服务通知ID */
        const val FOREGROUND_SERVICE_NOTIFICATION_ID = -1

        /** 日志标签（AppLogger 埋点统一使用） */
        private const val TAG = "NotifyApp"
    }

    override fun onCreate() {
        super.onCreate()
        // 日志系统初始化须先于任何埋点与黑匣子注册
        AppLogger.init(this)
        AppLogger.trimIfNeededAsync()
        installCrashLogger()
        AppLogger.i(TAG, "应用启动（versionCode=${Build.VERSION.SDK_INT} 设备 Android）")
        createNotificationChannels()
    }

    /**
     * 注册崩溃黑匣子（全局未捕获异常处理器）
     *
     * 实现思路：包装系统默认处理器，先将完整堆栈同步写入日志文件
     * （writeCrashBlocking 内部全体容错，落盘失败不拦截默认崩溃流程），
     * 再交还系统默认处理器继续原生崩溃行为（弹出崩溃对话框/结束进程）。
     * 修改（2026-08-16 15:39 | 图片通知闪退修复与日志导出）
     */
    private fun installCrashLogger() {
        val systemDefaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLogger.writeCrashBlocking(thread, throwable)
            systemDefaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * 创建通知渠道
     *
     * Android 8.0+ 必须创建通知渠道才能显示通知。
     * 创建两个渠道：
     * - CHANNEL_PINNED：用于用户创建的固定通知，高重要性
     * - CHANNEL_SERVICE：用于前台服务通知，低重要性
     */
    private fun createNotificationChannels() {
        // Android 8.0 以下无需创建渠道
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = getSystemService(NotificationManager::class.java)

        // 固定通知渠道 - 高重要性，用户可见
        val pinnedChannel = NotificationChannel(
            CHANNEL_PINNED,
            getString(R.string.notification_channel_pinned),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notification_channel_pinned_desc)
            setShowBadge(true)
            enableVibration(true)
        }

        // 前台服务渠道 - 低重要性，不打扰用户
        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            getString(R.string.notification_service_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_service_channel_desc)
            setShowBadge(false)
            enableVibration(false)
        }

        notificationManager.createNotificationChannels(listOf(pinnedChannel, serviceChannel))
    }
}
