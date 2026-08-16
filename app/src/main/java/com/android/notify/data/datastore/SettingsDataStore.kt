package com.android.notify.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore 实例（文件顶层单例）
 *
 * 修复（2026-08-16 10:40 | B3）：preferencesDataStore 委托必须声明在文件顶层。
 * 原实现声明在类体内，每次实例化 SettingsDataStore 都会创建新的委托对象，
 * 多实例并存时 DataStore 抛出 "multiple DataStores active for the same file" 异常。
 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 通知设置快照
 *
 * 单次读取 DataStore 的全部通知相关设置项。
 * 批量发送/恢复通知时复用同一份快照，避免每条通知重复读取 5 次 DataStore。
 * 优化（2026-08-16 10:40 | P2）
 *
 * @param showCopyButton 是否显示复制按钮
 * @param showEditButton 是否显示编辑按钮
 * @param showUnpinButton 是否显示取消固定按钮
 * @param multilineDisplay 是否多行显示
 * @param antiDeleteProtection 是否启用防删除保护
 * @param resendSoundEnabled 历史重发时是否响铃提醒
 */
data class NotificationSettingsSnapshot(
    val showCopyButton: Boolean = true,
    val showEditButton: Boolean = true,
    val showUnpinButton: Boolean = true,
    val multilineDisplay: Boolean = true,
    val antiDeleteProtection: Boolean = true,
    val resendSoundEnabled: Boolean = true
)

/**
 * 应用设置数据仓库
 *
 * 使用 DataStore Preferences 持久化存储用户偏好设置。
 * 所有设置项通过 Flow 暴露，支持响应式更新。
 *
 * 创建日期：2026-05-14 | 作者：Cline
 * 优化（2026-08-16）：B3 委托顶层单例化 + P2 新增设置快照单次读取
 */
class SettingsDataStore(private val context: Context) {

    // ===== 偏好键定义 =====

    /** 通知栏显示复制按钮 */
    private val keyShowCopyButton = booleanPreferencesKey("show_copy_button")

    /** 通知栏显示编辑按钮 */
    private val keyShowEditButton = booleanPreferencesKey("show_edit_button")

    /** 通知栏显示取消固定按钮 */
    private val keyShowUnpinButton = booleanPreferencesKey("show_unpin_button")

    /** 多行通知显示 */
    private val keyMultilineDisplay = booleanPreferencesKey("multiline_display")

    /** 防删除保护开关 */
    private val keyAntiDeleteProtection = booleanPreferencesKey("anti_delete_protection")

    /** 历史重发响铃提醒开关（B10 新增） */
    private val keyResendSoundEnabled = booleanPreferencesKey("resend_sound_enabled")

    /** 深色模式：system/light/dark */
    private val keyDarkMode = stringPreferencesKey("dark_mode")

    /** 语言：system/zh/en */
    private val keyLanguage = stringPreferencesKey("language")

    // ===== 读取接口（Flow） =====

    /** 是否显示复制按钮，默认true */
    val showCopyButton: Flow<Boolean> = context.settingsDataStore.data.map { it[keyShowCopyButton] ?: true }

    /** 是否显示编辑按钮，默认true */
    val showEditButton: Flow<Boolean> = context.settingsDataStore.data.map { it[keyShowEditButton] ?: true }

    /** 是否显示取消固定按钮，默认true */
    val showUnpinButton: Flow<Boolean> = context.settingsDataStore.data.map { it[keyShowUnpinButton] ?: true }

    /** 是否启用多行显示，默认true */
    val multilineDisplay: Flow<Boolean> = context.settingsDataStore.data.map { it[keyMultilineDisplay] ?: true }

    /** 是否启用防删除保护，默认true */
    val antiDeleteProtection: Flow<Boolean> = context.settingsDataStore.data.map { it[keyAntiDeleteProtection] ?: true }

    /** 历史重发是否响铃提醒，默认true */
    val resendSoundEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[keyResendSoundEnabled] ?: true }

    /** 深色模式设置，默认跟随系统 */
    val darkMode: Flow<String> = context.settingsDataStore.data.map { it[keyDarkMode] ?: "system" }

    /** 语言设置，默认跟随系统 */
    val language: Flow<String> = context.settingsDataStore.data.map { it[keyLanguage] ?: "system" }

    /**
     * 获取通知设置快照（单次 DataStore 读取）
     *
     * 优化（2026-08-16 | P2）：批量发送通知时每条通知读取 5 个 Flow
     * 造成 N×5 次 DataStore IO；快照方式将整批恢复的读取次数降为 1 次。
     *
     * @return 全部通知相关设置的快照
     */
    suspend fun getSnapshot(): NotificationSettingsSnapshot {
        val prefs = context.settingsDataStore.data.first()
        return NotificationSettingsSnapshot(
            showCopyButton = prefs[keyShowCopyButton] ?: true,
            showEditButton = prefs[keyShowEditButton] ?: true,
            showUnpinButton = prefs[keyShowUnpinButton] ?: true,
            multilineDisplay = prefs[keyMultilineDisplay] ?: true,
            antiDeleteProtection = prefs[keyAntiDeleteProtection] ?: true,
            resendSoundEnabled = prefs[keyResendSoundEnabled] ?: true
        )
    }

    // ===== 写入接口（suspend） =====

    /**
     * 设置是否显示复制按钮
     * @param value true显示，false隐藏
     */
    suspend fun setShowCopyButton(value: Boolean) {
        context.settingsDataStore.edit { it[keyShowCopyButton] = value }
    }

    /**
     * 设置是否显示编辑按钮
     * @param value true显示，false隐藏
     */
    suspend fun setShowEditButton(value: Boolean) {
        context.settingsDataStore.edit { it[keyShowEditButton] = value }
    }

    /**
     * 设置是否显示取消固定按钮
     * @param value true显示，false隐藏
     */
    suspend fun setShowUnpinButton(value: Boolean) {
        context.settingsDataStore.edit { it[keyShowUnpinButton] = value }
    }

    /**
     * 设置是否启用多行显示
     * @param value true启用，false单行
     */
    suspend fun setMultilineDisplay(value: Boolean) {
        context.settingsDataStore.edit { it[keyMultilineDisplay] = value }
    }

    /**
     * 设置是否启用防删除保护
     * @param value true启用，false禁用
     */
    suspend fun setAntiDeleteProtection(value: Boolean) {
        context.settingsDataStore.edit { it[keyAntiDeleteProtection] = value }
    }

    /**
     * 设置历史重发是否响铃提醒
     * @param value true响铃提醒，false静默弹出
     */
    suspend fun setResendSoundEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[keyResendSoundEnabled] = value }
    }

    /**
     * 设置深色模式
     * @param value "system"跟随系统, "light"浅色, "dark"深色
     */
    suspend fun setDarkMode(value: String) {
        context.settingsDataStore.edit { it[keyDarkMode] = value }
    }

    /**
     * 设置语言
     * @param value "system"跟随系统, "zh"中文, "en"英文
     */
    suspend fun setLanguage(value: String) {
        context.settingsDataStore.edit { it[keyLanguage] = value }
    }
}
