package com.android.notify.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.android.notify.data.db.dao.NotificationDao
import com.android.notify.data.db.entity.NotificationEntity

/**
 * Room 数据库定义
 *
 * 提供单例访问模式，确保全局只使用一个数据库实例。
 * 当前版本：2
 *
 * 创建日期：2026-05-14
 * 作者：Cline
 *
 * 新增（2026-08-16 | 图片通知）：version 1→2 增加 imagePath 列（ALTER TABLE，
 * 仅增可空列、无数据改写，历史记录完好无损）。
 */
@Database(
    entities = [NotificationEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    /** 获取通知数据访问对象 */
    abstract fun notificationDao(): NotificationDao

    companion object {
        /** 数据库文件名 */
        private const val DATABASE_NAME = "notify_app.db"

        /**
         * 数据库迁移：version 1 → 2
         *
         * 图片通知功能（2026-08-16）：为 notifications 表增加 imagePath 可空列，
         * 存储图片在应用私有目录中的路径。仅 ADD COLUMN，无数据改写，
         * 老版本数据升级后完整保留。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notifications ADD COLUMN imagePath TEXT")
            }
        }

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
                    // 修复（2026-08-16 | P4）：移除 allowMainThreadQueries。
                    // 该调用与注释意图相反——它允许主线程查询而非禁止，
                    // 主线程访问数据库会造成 UI 卡顿甚至 ANR。
                    // 当前全部 DAO 访问均在协程 Dispatchers.IO 中执行。
                    // 正式迁移（2026-08-16）：MIGRATION_1_2 保留用户历史数据；
                    // fallbackToDestructiveMigration 仅作为兜底（未知版本差异时重建）
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
