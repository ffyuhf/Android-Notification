package io.github.ffyuhf.notify.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.ffyuhf.notify.data.db.dao.NotificationDao
import io.github.ffyuhf.notify.data.db.entity.NotificationEntity

/**
 * Room 数据库定义
 *
 * 提供单例访问模式，确保全局只使用一个数据库实例。
 * 当前版本：2（version 1→2 经 ALTER TABLE 增加 imagePath 可空列，历史数据完整保留）。
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
         * 为 notifications 表增加 imagePath 可空列，存储图片在应用私有目录中的路径。
         * 仅 ADD COLUMN，无数据改写，老版本数据升级后完整保留。
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
                    // 不启用 allowMainThreadQueries：主线程访问数据库会造成 UI 卡顿甚至 ANR，
                    // 全部 DAO 访问须在协程 Dispatchers.IO 中执行。
                    // fallbackToDestructiveMigration 仅作未知版本差异时的兜底重建。
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
