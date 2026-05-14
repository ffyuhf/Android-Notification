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
