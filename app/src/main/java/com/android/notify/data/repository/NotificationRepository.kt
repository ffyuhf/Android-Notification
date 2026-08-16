package com.android.notify.data.repository

import android.content.Context
import com.android.notify.data.db.AppDatabase
import com.android.notify.data.db.dao.NotificationDao
import com.android.notify.data.db.entity.NotificationEntity
import kotlinx.coroutines.flow.Flow

/**
 * 通知数据仓库
 *
 * 封装 Room DAO 操作，提供统一的数据访问接口。
 * ViewModel 通过此仓库访问数据层，实现关注点分离。
 *
 * 创建日期：2026-05-14
 * 作者：Cline
 */
class NotificationRepository private constructor(context: Context) {

    /** Room DAO 实例 */
    private val dao: NotificationDao = AppDatabase.getInstance(context).notificationDao()

    /**
     * 获取所有通知记录（按创建时间降序）
     * 用于历史记录列表展示
     */
    val allNotifications: Flow<List<NotificationEntity>> = dao.getAllNotifications()

    /**
     * 插入新通知记录
     *
     * @param notification 通知实体
     * @return 插入后的行ID
     */
    suspend fun insertNotification(notification: NotificationEntity): Long {
        return dao.insert(notification)
    }

    /**
     * 更新通知记录
     *
     * @param notification 通知实体
     */
    suspend fun updateNotification(notification: NotificationEntity) {
        dao.update(notification)
    }

    /**
     * 按ID删除通知记录
     *
     * @param id 数据库主键ID
     */
    suspend fun deleteById(id: Int) {
        dao.deleteById(id)
    }

    /**
     * 批量删除通知记录（H2 新增，多选删除使用）
     *
     * @param ids 数据库主键ID集合
     */
    suspend fun deleteByIds(ids: List<Int>) {
        dao.deleteByIds(ids)
    }

    /**
     * 删除所有通知记录
     */
    suspend fun deleteAll() {
        dao.deleteAll()
    }

    /**
     * 按ID查询通知记录
     *
     * @param id 数据库主键ID
     * @return 通知实体，不存在返回null
     */
    suspend fun getById(id: Int): NotificationEntity? {
        return dao.getById(id)
    }

    /**
     * 按通知栏ID查询通知记录
     *
     * @param notificationId Android 通知栏ID
     * @return 通知实体，不存在返回null
     */
    suspend fun getByNotificationId(notificationId: Int): NotificationEntity? {
        return dao.getByNotificationId(notificationId)
    }

    /**
     * 获取所有活跃且固定的通知
     * 用于前台服务巡检恢复
     *
     * @return 活跃固定通知列表
     */
    suspend fun getActivePinnedNotifications(): List<NotificationEntity> {
        return dao.getActivePinnedNotifications()
    }

    /**
     * 获取所有活跃固定通知的通知栏ID列表
     * 用于巡检对比
     *
     * @return 通知栏ID列表
     */
    suspend fun getActivePinnedNotificationIds(): List<Int> {
        return dao.getActivePinnedNotificationIds()
    }

    /**
     * 将通知标记为非活跃
     * 取消固定时调用
     *
     * @param id 数据库主键ID
     */
    suspend fun deactivateById(id: Int) {
        dao.deactivateById(id)
    }

    /**
     * 将通知标记为活跃
     * 定时通知触发后调用，纳入防删除三层保护（B9 修复）
     *
     * @param id 数据库主键ID
     */
    suspend fun activateById(id: Int) {
        dao.activateById(id)
    }

    /**
     * 更新定时触发时间
     *
     * 重复通知触发后回写下次触发时间，作为下次周期计算基准，
     * 避免以系统当前时间为基准导致的累积漂移。
     * 修复（2026-08-16 10:40 | B6）
     *
     * @param id 数据库主键ID
     * @param scheduledAt 下一次触发时间戳（毫秒）
     */
    suspend fun updateScheduledAt(id: Int, scheduledAt: Long) {
        dao.updateScheduledAt(id, scheduledAt)
    }

    /**
     * 更新通知内容
     * 编辑功能使用
     *
     * @param id 数据库主键ID
     * @param title 新标题
     * @param content 新内容
     */
    suspend fun updateContent(id: Int, title: String?, content: String) {
        dao.updateContent(id, title, content)
    }

    /**
     * 获取通知记录总数
     * 用于生成不重复的通知栏ID
     *
     * @return 记录总数
     */
    suspend fun getCount(): Int {
        return dao.getCount()
    }

    /**
     * 判断通知栏ID是否已被占用
     * 生成通知栏ID时的唯一性校验（优化 2026-08-16 | P6）
     *
     * @param notificationId 通知栏ID
     * @return true表示已存在
     */
    suspend fun existsByNotificationId(notificationId: Int): Boolean {
        return dao.existsByNotificationId(notificationId)
    }

    companion object {
        @Volatile
        private var INSTANCE: NotificationRepository? = null

        /**
         * 获取仓库单例
         *
         * @param context 应用上下文
         * @return NotificationRepository 单例
         */
        fun getInstance(context: Context): NotificationRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NotificationRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
