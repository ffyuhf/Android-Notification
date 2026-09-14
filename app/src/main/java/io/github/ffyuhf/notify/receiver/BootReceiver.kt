package io.github.ffyuhf.notify.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.ffyuhf.notify.service.NotifyForegroundService

/**
 * 开机自启接收器
 *
 * 监听系统启动完成广播，自动启动前台服务恢复所有固定通知。
 * 仅处理解锁后的 BOOT_COMPLETED：锁屏直启（direct boot）阶段应用只能访问
 * 设备加密（DE）存储，而 Room/DataStore 位于凭据加密（CE）存储，此时启动服务会崩溃。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                NotifyForegroundService.start(context)
            }
        }
    }
}
