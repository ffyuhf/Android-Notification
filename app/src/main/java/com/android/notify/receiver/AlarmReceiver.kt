package com.android.notify.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.android.notify.data.datastore.SettingsDataStore
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
 * 3. 如果是重复通知，以上次理论触发时间为基准计算下次触发并重新调度
 *
 * 修复（2026-08-16）：
 * - B6 重复通知漂移：原以 System.currentTimeMillis() 为基准计算下次触发，
 *   每周期累积偏差；现以 entity.scheduledAt 为基准并回写，保持周期稳定
 * - B7 使用 goAsync() 延长 Receiver 生命周期，确保异步操作完成
 *
 * 创建日期：2026-05-14 | 作者：Cline
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val dbId = intent.getIntExtra(EXTRA_DB_ID, -1)
        if (dbId == -1) return

        // goAsync 延长 Receiver 生命周期，确保数据库与通知操作在进程被回收前完成（B7）
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = NotificationRepository.getInstance(context)
                val entity = repository.getById(dbId) ?: return@launch

                // 发送通知
                val settings = SettingsDataStore(context)
                NotificationHelper.sendNotification(context, entity, settings.getSnapshot())

                // 处理重复通知：以上次理论触发时间为基准（B6）
                if (entity.repeatType != null) {
                    scheduleNextRepeating(context, repository, entity)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * 计算并调度下一次重复触发
     *
     * 以 entity.scheduledAt（上次理论触发时间）为基准计算，并回写数据库，
     * 避免以系统当前时间为基准导致的周期累积漂移。
     * 若基准时间已落后于当前时间（如设备长期关机），向后顺延至未来再触发。
     *
     * @param context 上下文
     * @param repository 数据仓库
     * @param entity 当次触发的通知实体
     */
    private suspend fun scheduleNextRepeating(
        context: Context,
        repository: NotificationRepository,
        entity: com.android.notify.data.db.entity.NotificationEntity
    ) {
        val repeatType = entity.repeatType ?: return
        val baseTime = entity.scheduledAt ?: System.currentTimeMillis()

        // 基准落后时按周期顺延至未来，避免补发风暴
        var nextTime = AlarmScheduler.calculateNextTriggerTime(repeatType, baseTime)
        while (nextTime <= System.currentTimeMillis()) {
            nextTime = AlarmScheduler.calculateNextTriggerTime(repeatType, nextTime)
        }

        // 回写下次触发时间作为后续计算基准，消除累积漂移（B6）
        repository.updateScheduledAt(entity.id, nextTime)
        AlarmScheduler.scheduleRepeating(context, entity, nextTime)
    }

    companion object {
        /** Intent Extra：通知记录数据库ID（与 AlarmScheduler 保持一致） */
        private const val EXTRA_DB_ID = "alarm_notification_id"
    }
}
