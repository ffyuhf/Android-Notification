package com.android.notify.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.android.notify.data.db.entity.NotificationEntity
import com.android.notify.receiver.AlarmReceiver
import java.util.Calendar

/**
 * 定时通知调度器
 *
 * 使用 AlarmManager 精确定时触发通知。
 * 支持一次性定时和重复（时/日/周/月/年）。
 *
 * 创建日期：2026-05-14
 * 作者：Cline
 */
object AlarmScheduler {

    /** Alarm Intent Extra：通知记录数据库ID */
    private const val EXTRA_NOTIFICATION_ID = "alarm_notification_id"

    /**
     * 调度定时通知
     *
     * 根据 repeatType 设置精确闹钟或重复闹钟。
     * Android 12+ 使用精确闹钟需 SCHEDULE_EXACT_ALARM 权限。
     *
     * @param context 上下文
     * @param entity 通知实体（必须包含有效的 scheduledAt 和 notificationId）
     */
    fun schedule(context: Context, entity: NotificationEntity) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = createPendingIntent(context, entity.id, entity.notificationId)

        val triggerTime = entity.scheduledAt ?: return

        // 检查精确闹钟权限（Android 12+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                // 无精确闹钟权限，使用不精确闹钟
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
                return
            }
        }

        // 设置精确闹钟
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )
    }

    /**
     * 调度重复通知
     *
     * 根据重复类型计算下一次触发时间并设置闹钟。
     * 使用 setExactAndAllowWhileIdle 确保在 Doze 模式下也能触发。
     *
     * @param context 上下文
     * @param entity 通知实体
     * @param nextTriggerTime 下一次触发时间戳（毫秒）
     */
    fun scheduleRepeating(context: Context, entity: NotificationEntity, nextTriggerTime: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = createPendingIntent(context, entity.id, entity.notificationId)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTriggerTime,
                    pendingIntent
                )
                return
            }
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextTriggerTime,
            pendingIntent
        )
    }

    /**
     * 取消定时通知
     *
     * @param context 上下文
     * @param dbId 数据库ID
     * @param notificationId 通知栏ID
     */
    fun cancel(context: Context, dbId: Int, notificationId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = createPendingIntent(context, dbId, notificationId)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /**
     * 计算下一次重复触发时间
     *
     * @param repeatType 重复类型：hourly/daily/weekly/monthly/yearly
     * @param baseTime 基准时间戳（毫秒）
     * @return 下一次触发时间戳（毫秒）
     */
    fun calculateNextTriggerTime(repeatType: String?, baseTime: Long): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = baseTime }

        when (repeatType) {
            "hourly" -> calendar.add(Calendar.HOUR_OF_DAY, 1)
            "daily" -> calendar.add(Calendar.DAY_OF_YEAR, 1)
            "weekly" -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
            "monthly" -> calendar.add(Calendar.MONTH, 1)
            "yearly" -> calendar.add(Calendar.YEAR, 1)
            else -> return baseTime
        }

        return calendar.timeInMillis
    }

    /**
     * 创建闹钟 PendingIntent
     *
     * @param context 上下文
     * @param dbId 数据库ID
     * @param notificationId 通知栏ID
     * @return PendingIntent
     */
    private fun createPendingIntent(context: Context, dbId: Int, notificationId: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_NOTIFICATION_ID, dbId)
            putExtra(NotificationHelper.EXTRA_NOTIFICATION_BAR_ID, notificationId)
            action = "ALARM_${dbId}_${notificationId}"
        }

        return PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
