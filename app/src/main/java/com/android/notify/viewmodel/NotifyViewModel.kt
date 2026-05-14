package com.android.notify.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    /** 操作结果消息（用于Toast） */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

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

            // 保存到数据库
            repository.insertNotification(entity)

            // 发送到通知栏
            NotificationHelper.sendNotification(context, entity, settings)

            // 启动前台保活服务
            NotifyForegroundService.start(context)

            _message.value = "toast_notification_sent"
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

            // 保存到数据库
            repository.insertNotification(entity)

            // 设置定时闹钟
            AlarmScheduler.schedule(context, entity)

            _message.value = "toast_notification_scheduled"
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

            repository.insertNotification(newEntity)
            NotificationHelper.sendNotification(context, newEntity, settings)

            _message.value = "toast_notification_sent"
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
            NotificationHelper.sendNotification(context, updatedEntity, settings)
        }
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
