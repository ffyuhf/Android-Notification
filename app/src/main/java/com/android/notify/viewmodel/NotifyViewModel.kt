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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

    // ===== 历史记录搜索（H3 新增 2026-08-16） =====

    /** 历史记录搜索关键词 */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * 按关键词过滤后的历史记录（H3 新增）
     *
     * 匹配标题与内容，忽略大小写；关键词为空时返回全量。
     */
    val filteredNotifications: StateFlow<List<NotificationEntity>> =
        combine(allNotifications, searchQuery) { list, query ->
            if (query.isBlank()) {
                list
            } else {
                val keyword = query.trim()
                list.filter { entity ->
                    entity.title?.contains(keyword, ignoreCase = true) == true ||
                        entity.content.contains(keyword, ignoreCase = true)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 更新历史记录搜索关键词（H3） */
    fun setSearchQuery(value: String) {
        _searchQuery.value = value
    }

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

    /** 历史重发时是否响铃提醒 */
    val resendSoundEnabled: StateFlow<Boolean> = settings.resendSoundEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** 历史记录布局模式（H1 新增）：single单列 / two_column两列 */
    val historyLayoutMode: StateFlow<String> = settings.historyLayoutMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "single")

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
                // B9 修复：定时记录创建时为 isActive=false（未触发、不在通知栏），
                // 由 AlarmReceiver 到点触发后置为 true；避免被前台服务巡检
                // 误判为"被删除的固定通知"而提前补发（设定19分18分提前出现）
                isActive = false,
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
     * 修复（2026-08-16 | B10）：原实现 copy(id=0) 插入新记录，导致历史记录重复累积。
     * 现改为更新原记录而非插入：刷新 createdAt 实现置顶（列表按 createdAt 降序），
     * 清空定时字段避免未触发定时记录被置为 isActive=true 后重新进入误发路径，
     * 取消旧闹钟与旧通知，重新生成通知栏ID以响铃提醒（D3），
     * 并启动前台保活服务与"立即发送"行为对齐（D4）。
     *
     * @param entity 历史通知实体
     */
    fun resendNotification(entity: NotificationEntity) {
        viewModelScope.launch {
            resendNotificationInternal(entity)
        }
    }

    /**
     * 批量重新发送历史通知（H2 新增，多选发送使用）
     *
     * 逐条按现有 resend 链路执行：取消旧闹钟/旧通知 → 新通知栏ID → 更新原记录置顶 → 发送。
     *
     * @param entities 待重发的通知实体列表（按传入顺序逐条处理）
     */
    fun resendNotifications(entities: List<NotificationEntity>) {
        viewModelScope.launch {
            entities.forEach { resendNotificationInternal(it) }
        }
    }

    /**
     * 单条重发核心链路（H2 抽取，单条与批量共用）
     *
     * @param entity 历史通知实体
     */
    private suspend fun resendNotificationInternal(entity: NotificationEntity) {
        // 取消可能残留的定时闹钟与通知栏旧 ID 通知（B11 清理逻辑复用）
        cancelNotificationSideEffects(entity)

        // 生成新通知栏ID：新 ID 首次发布可响铃提醒，不受 setOnlyAlertOnce 静默影响
        val notificationId = NotificationHelper.generateNotificationId(context)

        // 更新原记录：createdAt 置顶；清空定时字段转为即时通知
        val updatedEntity = entity.copy(
            createdAt = System.currentTimeMillis(),
            scheduledAt = null,
            repeatType = null,
            isActive = true,
            isPinned = true,
            notificationId = notificationId
        )
        repository.updateNotification(updatedEntity)

        // 重发是否响铃由用户设置决定（默认开启，可关闭静默弹出）
        val snapshot = settings.getSnapshot()
        NotificationHelper.sendNotification(
            context,
            updatedEntity,
            snapshot,
            soundEnabled = snapshot.resendSoundEnabled
        )

        // 与"立即发送"行为对齐：启动前台保活服务（D4）
        NotifyForegroundService.start(context)

        _message.value = R.string.toast_notification_sent
    }

    /**
     * 删除前清理该记录的通知栏通知与残留定时闹钟（B11 新增）
     *
     * 修复（2026-08-16 12:51 | B11）：原删除仅删库，通知栏残留、闹钟仍触发。
     * 两清理方法均幂等，对已不存在/未设置的 ID 无副作用。
     *
     * @param entity 待删除的通知实体
     */
    private suspend fun cancelNotificationSideEffects(entity: NotificationEntity) {
        AlarmScheduler.cancel(context, entity.id, entity.notificationId)
        NotificationHelper.cancelNotification(context, entity.notificationId)
    }

    /**
     * 删除历史通知记录
     *
     * 优化（2026-08-16 12:51 | B11）：删除前先清理该记录的通知栏通知与残留闹钟，
     * 避免删库后通知栏残留、定时闹钟仍触发。
     *
     * @param id 数据库ID
     */
    fun deleteNotification(id: Int) {
        viewModelScope.launch {
            val entity = repository.getById(id) ?: return@launch
            cancelNotificationSideEffects(entity)
            repository.deleteById(id)
        }
    }

    /**
     * 批量删除历史通知记录（H2 新增，多选删除使用）
     *
     * 删除前逐条清理通知栏通知与残留闹钟（B11 链路）。
     *
     * @param ids 数据库主键ID集合
     */
    fun deleteNotifications(ids: List<Int>) {
        viewModelScope.launch {
            val entities = repository.allNotifications.first().filter { it.id in ids }
            entities.forEach { cancelNotificationSideEffects(it) }
            repository.deleteByIds(ids)
        }
    }

    /**
     * 清空所有历史记录
     *
     * 优化（2026-08-16 12:51 | B11）：清空前逐条清理通知栏通知与残留闹钟。
     */
    fun deleteAllNotifications() {
        viewModelScope.launch {
            repository.allNotifications.first().forEach { cancelNotificationSideEffects(it) }
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

    /** 设置历史重发是否响铃提醒 */
    fun setResendSoundEnabled(value: Boolean) { viewModelScope.launch { settings.setResendSoundEnabled(value) } }

    /** 设置历史记录布局模式（H1）："single"单列, "two_column"两列 */
    fun setHistoryLayoutMode(value: String) { viewModelScope.launch { settings.setHistoryLayoutMode(value) } }

    /**
     * 清除消息状态
     */
    fun clearMessage() {
        _message.value = null
    }
}
