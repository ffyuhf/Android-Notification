package com.android.notify.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.notify.R
import com.android.notify.data.datastore.SettingsDataStore
import com.android.notify.data.db.entity.NotificationEntity
import com.android.notify.data.repository.NotificationRepository
import com.android.notify.service.NotifyForegroundService
import com.android.notify.util.AlarmScheduler
import com.android.notify.util.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 主 ViewModel
 *
 * 管理通知的创建、发送、定时、历史记录和设置。
 * 作为 UI 层与数据层的桥梁。
 *
 * 创建日期：2026-05-14
 * 作者：Cline
 */
class NotifyViewModel(application: Application) : AndroidViewModel(application) {

    /** 通知数据仓库 */
    private val repository = NotificationRepository.getInstance(application)

    /** 设置数据仓库 */
    private val settings = SettingsDataStore(application)

    /** 应用上下文 */
    private val context = application.applicationContext

    // ===== 通知历史记录 =====

    /** 所有通知记录（按时间降序），响应式 Flow */
    val allNotifications: StateFlow<List<NotificationEntity>> = repository.allNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ===== 设置状态 =====

    /** 是否显示复制按钮 */
    val showCopyButton: StateFlow<Boolean> = settings.showCopyButton
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** 是否显示编辑按钮 */
    val showEditButton: StateFlow<Boolean> = settings.showEditButton
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** 是否显示取消固定按钮 */
    val showUnpinButton: StateFlow<Boolean> = settings.showUnpinButton
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** 是否启用多行显示 */
    val multilineDisplay: StateFlow<Boolean> = settings.multilineDisplay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** 是否启用防删除保护 */
    val antiDeleteProtection: StateFlow<Boolean> = settings.antiDeleteProtection
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** 深色模式设置 */
    val darkMode: StateFlow<String> = settings.darkMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    /** 语言设置 */
    val language: StateFlow<String> = settings.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    // ===== UI 状态 =====

    /**
     * 操作结果消息（字符串资源ID，用于Toast）
     * 优化（2026-08-16 | B5）：值改为资源ID，由UI层统一消费显示，原key字符串无消费者
     */
    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()

    /**
     * 立即发送通知
     *
     * 创建通知记录并立即显示到通知栏。
     * 同时保存到历史记录并启动前台服务。
     *
     * @param title 通知标题（可选）
     * @param content 通知内容
     * @param isPinned 是否固定显示
     */
    fun sendNow(title: String?, content: String, isPinned: Boolean = true) {
        viewModelScope.launch {
            val notificationId = NotificationHelper.generateNotificationId(context)
            val currentTime = System.currentTimeMillis()

            val entity = NotificationEntity(
                title = title,
                content = content,
                createdAt = currentTime,
                isPinned = isPinned,
                isActive = true,
                notificationId = notificationId,
                isAntiDeleteEnabled = true
            )

            // 保存到数据库并获取真实ID，确保Intent中携带正确的数据库ID
            val rowId = repository.insertNotification(entity)
            val savedEntity = entity.copy(id = rowId.toInt())

            // 发送到通知栏（使用包含真实ID的entity，复用单次设置快照）
            NotificationHelper.sendNotification(context, savedEntity, settings.getSnapshot())

            // 启动前台保活服务
            NotifyForegroundService.start(context)

            _message.value = R.string.toast_notification_sent
        }
    }

    /**
     * 定时发送通知
     *
     * 创建通知记录并设置 AlarmManager 定时触发。
     *
     * @param title 通知标题（可选）
     * @param content 通知内容
     * @param scheduledAt 定时触发时间戳（毫秒）
     * @param repeatType 重复类型（null/时/日/周/月/年）
     * @param isPinned 是否固定显示
     */
    fun scheduleNotification(
        title: String?,
        content: String,
        scheduledAt: Long,
        repeatType: String?,
        isPinned: Boolean = true
    ) {
        viewModelScope.launch {
            val notificationId = NotificationHelper.generateNotificationId(context)
            val currentTime = System.currentTimeMillis()

            val entity = NotificationEntity(
                title = title,
                content = content,
                createdAt = currentTime,
                scheduledAt = scheduledAt,
                repeatType = repeatType,
                isPinned = isPinned,
                isActive = true,
                notificationId = notificationId,
                isAntiDeleteEnabled = true
            )

            // 保存到数据库并回填真实ID（修复 2026-08-16 | B1）：
            // 原实现调度闹钟时使用未回填的 entity（id=0），
            // 触发时按 dbId=0 查库为空直接返回，定时通知永不发出
            val rowId = repository.insertNotification(entity)
            val savedEntity = entity.copy(id = rowId.toInt())

            // 设置定时闹钟（使用包含真实ID的entity）
            AlarmScheduler.schedule(context, savedEntity)

            _message.value = R.string.toast_notification_scheduled
        }
    }

    /**
     * 重新发送历史通知
     *
     * 从历史记录中选择一条通知重新发送。
     *
     * @param entity 历史通知实体
     */
    fun resendNotification(entity: NotificationEntity) {
        viewModelScope.launch {
            val notificationId = NotificationHelper.generateNotificationId(context)

            val newEntity = entity.copy(
                id = 0,
                createdAt = System.currentTimeMillis(),
                notificationId = notificationId,
                isActive = true,
                isPinned = true
            )

            // 保存到数据库并获取真实ID，确保Intent中携带正确的数据库ID
            val rowId = repository.insertNotification(newEntity)
            val savedEntity = newEntity.copy(id = rowId.toInt())

            NotificationHelper.sendNotification(context, savedEntity, settings.getSnapshot())

            _message.value = R.string.toast_notification_sent
        }
    }

    /**
     * 删除历史通知记录
     *
     * @param id 数据库ID
     */
    fun deleteNotification(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    /**
     * 清空所有历史记录
     */
    fun deleteAllNotifications() {
        viewModelScope.launch {
            repository.deleteAll()
        }
    }

    /**
     * 取消固定通知
     *
     * @param id 数据库ID
     * @param notificationBarId 通知栏ID
     */
    fun unpinNotification(id: Int, notificationBarId: Int) {
        viewModelScope.launch {
            repository.deactivateById(id)
            NotificationHelper.cancelNotification(context, notificationBarId)
        }
    }

    /**
     * 更新通知内容（编辑功能）
     *
     * @param id 数据库ID
     * @param title 新标题
     * @param content 新内容
     */
    fun updateNotificationContent(id: Int, title: String?, content: String) {
        viewModelScope.launch {
            repository.updateContent(id, title, content)

            // 重新发送更新后的通知
            val entity = repository.getById(id) ?: return@launch
            val updatedEntity = entity.copy(title = title, content = content)
            NotificationHelper.sendNotification(context, updatedEntity, settings.getSnapshot())
        }
    }

    /**
     * 按ID加载通知记录
     *
     * 供编辑页读取通知数据（优化 2026-08-16 | U5）：
     * 原编辑页直接访问 Repository 越过 ViewModel，违反 MVVM 分层。
     *
     * @param id 数据库ID
     * @return 通知实体，不存在返回null
     */
    suspend fun loadNotification(id: Int): NotificationEntity? {
        return repository.getById(id)
    }

    // ===== 设置操作 =====

    /** 设置是否显示复制按钮 */
    fun setShowCopyButton(value: Boolean) { viewModelScope.launch { settings.setShowCopyButton(value) } }

    /** 设置是否显示编辑按钮 */
    fun setShowEditButton(value: Boolean) { viewModelScope.launch { settings.setShowEditButton(value) } }

    /** 设置是否显示取消固定按钮 */
    fun setShowUnpinButton(value: Boolean) { viewModelScope.launch { settings.setShowUnpinButton(value) } }

    /** 设置是否启用多行显示 */
    fun setMultilineDisplay(value: Boolean) { viewModelScope.launch { settings.setMultilineDisplay(value) } }

    /** 设置是否启用防删除保护 */
    fun setAntiDeleteProtection(value: Boolean) { viewModelScope.launch { settings.setAntiDeleteProtection(value) } }

    /** 设置深色模式 */
    fun setDarkMode(value: String) { viewModelScope.launch { settings.setDarkMode(value) } }

    /** 设置语言 */
    fun setLanguage(value: String) { viewModelScope.launch { settings.setLanguage(value) } }

    /**
     * 清除消息状态
     */
    fun clearMessage() {
        _message.value = null
    }
}
