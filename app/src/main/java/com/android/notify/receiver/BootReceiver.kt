package com.android.notify.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.android.notify.service.NotifyForegroundService

/**
 * 开机自启接收器
 *
 * 监听系统启动完成广播，自动启动前台服务恢复所有固定通知。
 * 支持标准开机、快速开机、锁屏开机三种广播。
 *
 * 创建日期：2026-05-14
 * 作者：Cline
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                // 启动前台服务，服务内部会自动恢复所有固定通知
                NotifyForegroundService.start(context)
            }
        }
    }
}
