package com.android.notify.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.android.notify.data.db.dao.NotificationDao
import com.android.notify.data.db.entity.NotificationEntity

/**
 * Room 数据库定义
 *
 * 提供单例访问模式，确保全局只使用一个数据库实例。
 * 当前版本：1
 *
 * 创建日期：2026-05-14
 * 作者：Cline
 */
@Database(
    entities = [NotificationEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    /** 获取通知数据访问对象 */
    abstract fun notificationDao(): NotificationDao

    companion object {
        /** 数据库文件名 */
        private const val DATABASE_NAME = "notify_app.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * 获取数据库单例
         *
         * 使用双重检查锁定模式确保线程安全。
         *
         * @param context 应用上下文
         * @return AppDatabase 单例实例
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    // 主线程查询禁止（强制在IO线程执行）
                    .allowMainThreadQueries()
                    // 数据库损坏时自动重建
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
