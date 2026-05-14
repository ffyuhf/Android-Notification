package com.android.notify.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 通知记录实体
 *
 * 存储每条通知的完整信息，包括内容、时间、重复配置、状态等。
 * 对应数据库表：notifications
 *
 * 创建日期：2026-05-14
 * 作者：Cline
 */
@Entity(
    tableName = "notifications",
    indices = [
        // 按创建时间索引，用于历史记录按时间排序查询
        Index(value = ["createdAt"]),
        // 按通知栏ID索引，用于通知操作时快速查找
        Index(value = ["notificationId"]),
        // 按活跃状态索引，用于巡检恢复时查询所有活跃通知
        Index(value = ["isActive"])
    ]
)
data class NotificationEntity(
    /** 数据库自增主键 */
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    /** 通知标题（可选，为null时通知栏不显示标题行） */
    val title: String? = null,

    /** 通知正文内容 */
    val content: String,

    /** 创建时间戳（毫秒），记录用户创建通知的时间 */
    val createdAt: Long,

    /** 定时发送时间戳（毫秒），null表示即时发送 */
    val scheduledAt: Long? = null,

    /**
     * 重复类型，取值范围：
     * - null：不重复（一次性通知）
     * - "hourly"：每小时
     * - "daily"：每天
     * - "weekly"：每周
     * - "monthly"：每月
     * - "yearly"：每年
     */
    val repeatType: String? = null,

    /** 是否固定显示在通知栏（setOngoing） */
    val isPinned: Boolean = true,

    /** 是否当前活跃（通知栏中可见且未取消） */
    val isActive: Boolean = true,

    /**
     * Android 通知栏唯一ID
     * 用于 NotificationManager.notify() 的 ID 参数
     */
    val notificationId: Int,

    /** 是否启用防删除保护 */
    val isAntiDeleteEnabled: Boolean = true
)
