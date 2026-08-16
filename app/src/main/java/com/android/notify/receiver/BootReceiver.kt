package com.android.notify.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.android.notify.service.NotifyForegroundService

/**
 * 开机自启接收器
 *
 * 监听系统启动完成广播，自动启动前台服务恢复所有固定通知。
 *
 * 修复（2026-08-16 | P7）：移除 LOCKED_BOOT_COMPLETED 处理。
 * 锁屏直启（direct boot）阶段应用只能访问设备加密（DE）存储，
 * 而 Room/DataStore 位于凭据加密（CE）存储，此时启动服务会崩溃；
 * 通知恢复统一延迟到解锁后的 BOOT_COMPLETED。
 *
 * 创建日期：2026-05-14 | 作者：Cline
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                // 启动前台服务，服务内部会自动恢复所有固定通知
                NotifyForegroundService.start(context)
            }
        }
    }
}
