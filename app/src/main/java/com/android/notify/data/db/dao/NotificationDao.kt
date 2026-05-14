package com.android.notify.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.android.notify.data.db.entity.NotificationEntity
import kotlinx.coroutines.flow.Flow

/**
 * 通知数据访问对象（DAO）
 *
 * 提供通知记录的 CRUD 操作接口。
 * 所有返回列表的查询均使用 Flow 以支持响应式数据更新。
 *
 * 创建日期：2026-05-14
 * 作者：Cline
 */
@Dao
interface NotificationDao {

    /**
     * 插入新通知记录
     *
     * @param notification 通知实体
     * @return 插入后的行ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationEntity): Long

    /**
     * 更新已有通知记录
     *
     * @param notification 通知实体（必须包含有效ID）
     */
    @Update
    suspend fun update(notification: NotificationEntity)

    /**
     * 删除通知记录
     *
     * @param notification 通知实体
     */
    @Delete
    suspend fun delete(notification: NotificationEntity)

    /**
     * 按ID删除通知记录
     *
     * @param id 数据库主键ID
     */
    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteById(id: Int)

    /**
     * 删除所有通知记录
     */
    @Query("DELETE FROM notifications")
    suspend fun deleteAll()

    /**
     * 查询所有通知记录（按创建时间降序）
     *
     * @return 通知记录列表的 Flow
     */
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>

    /**
     * 按ID查询单条通知记录
     *
     * @param id 数据库主键ID
     * @return 通知实体，不存在返回null
     */
    @Query("SELECT * FROM notifications WHERE id = :id")
    suspend fun getById(id: Int): NotificationEntity?

    /**
     * 按通知栏ID查询单条通知记录
     *
     * @param notificationId Android 通知栏ID
     * @return 通知实体，不存在返回null
     */
    @Query("SELECT * FROM notifications WHERE notificationId = :notificationId")
    suspend fun getByNotificationId(notificationId: Int): NotificationEntity?

    /**
     * 查询所有活跃且固定的通知（用于巡检恢复）
     *
     * @return 活跃固定通知列表
     */
    @Query("SELECT * FROM notifications WHERE isActive = 1 AND isPinned = 1")
    suspend fun getActivePinnedNotifications(): List<NotificationEntity>

    /**
     * 获取所有活跃固定通知的通知栏ID列表（用于巡检对比）
     *
     * @return 通知栏ID列表
     */
    @Query("SELECT notificationId FROM notifications WHERE isActive = 1 AND isPinned = 1")
    suspend fun getActivePinnedNotificationIds(): List<Int>

    /**
     * 将指定通知标记为非活跃（取消固定时使用）
     *
     * @param id 数据库主键ID
     */
    @Query("UPDATE notifications SET isActive = 0 WHERE id = :id")
    suspend fun deactivateById(id: Int)

    /**
     * 更新通知内容（编辑功能使用）
     *
     * @param id 数据库主键ID
     * @param title 新标题
     * @param content 新内容
     */
    @Query("UPDATE notifications SET title = :title, content = :content WHERE id = :id")
    suspend fun updateContent(id: Int, title: String?, content: String)

    /**
     * 获取通知记录总数
     *
     * @return 记录总数
     */
    @Query("SELECT COUNT(*) FROM notifications")
    suspend fun getCount(): Int
}
