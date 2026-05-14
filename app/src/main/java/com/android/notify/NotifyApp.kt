package com.android.notify

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

/**
 * 应用程序入口类
 *
 * 职责：
 * 1. 创建通知渠道（Android 8.0+ 必须）
 * 2. 初始化全局组件
 *
 * 创建日期：2026-05-14
 * 作者：Cline
 */
class NotifyApp : Application() {

    companion object {
        /** 固定通知渠道ID */
        const val CHANNEL_PINNED = "channel_pinned"

        /** 前台服务通知渠道ID */
        const val CHANNEL_SERVICE = "channel_service"

        /** 前台服务通知ID */
        const val FOREGROUND_SERVICE_NOTIFICATION_ID = -1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
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
