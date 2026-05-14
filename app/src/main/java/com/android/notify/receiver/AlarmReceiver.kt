package com.android.notify.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.android.notify.data.datastore.SettingsDataStore
import com.android.notify.data.db.entity.NotificationEntity
import com.android.notify.data.repository.NotificationRepository
import com.android.notify.util.AlarmScheduler
import com.android.notify.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 定时通知触发接收器
 *
 * 当 AlarmManager 触发时：
 * 1. 从数据库读取通知记录
 * 2. 发送通知到通知栏
 * 3. 如果是重复通知，计算下次触发时间并重新调度
 *
 * 创建日期：2026-05-14
 * 作者：Cline
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val dbId = intent.getIntExtra("alarm_notification_id", -1)
        val notificationBarId = intent.getIntExtra(
            NotificationHelper.EXTRA_NOTIFICATION_BAR_ID, -1
        )

        if (dbId == -1) return

        // 在IO线程执行数据库和通知操作
        CoroutineScope(Dispatchers.IO).launch {
            val repository = NotificationRepository.getInstance(context)
            val entity = repository.getById(dbId) ?: return@launch

            // 发送通知
            val settings = SettingsDataStore(context)
            NotificationHelper.sendNotification(context, entity, settings)

            // 处理重复通知
            if (entity.repeatType != null) {
                val nextTime = AlarmScheduler.calculateNextTriggerTime(
                    entity.repeatType,
                    System.currentTimeMillis()
                )
                AlarmScheduler.scheduleRepeating(context, entity, nextTime)
            }
        }
    }
}
