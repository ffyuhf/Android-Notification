package com.android.notify.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 应用设置数据仓库
 *
 * 使用 DataStore Preferences 持久化存储用户偏好设置。
 * 所有设置项通过 Flow 暴露，支持响应式更新。
 *
 * 创建日期：2026-05-14
 * 作者：Cline
 */
class SettingsDataStore(private val context: Context) {

    /** DataStore 实例（懒加载，文件名：settings） */
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

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

    /** 深色模式：system/light/dark */
    private val keyDarkMode = stringPreferencesKey("dark_mode")

    /** 语言：system/zh/en */
    private val keyLanguage = stringPreferencesKey("language")

    // ===== 读取接口（Flow） =====

    /** 是否显示复制按钮，默认true */
    val showCopyButton: Flow<Boolean> = context.dataStore.data.map { it[keyShowCopyButton] ?: true }

    /** 是否显示编辑按钮，默认true */
    val showEditButton: Flow<Boolean> = context.dataStore.data.map { it[keyShowEditButton] ?: true }

    /** 是否显示取消固定按钮，默认true */
    val showUnpinButton: Flow<Boolean> = context.dataStore.data.map { it[keyShowUnpinButton] ?: true }

    /** 是否启用多行显示，默认true */
    val multilineDisplay: Flow<Boolean> = context.dataStore.data.map { it[keyMultilineDisplay] ?: true }

    /** 是否启用防删除保护，默认true */
    val antiDeleteProtection: Flow<Boolean> = context.dataStore.data.map { it[keyAntiDeleteProtection] ?: true }

    /** 深色模式设置，默认跟随系统 */
    val darkMode: Flow<String> = context.dataStore.data.map { it[keyDarkMode] ?: "system" }

    /** 语言设置，默认跟随系统 */
    val language: Flow<String> = context.dataStore.data.map { it[keyLanguage] ?: "system" }

    // ===== 写入接口（suspend） =====

    /**
     * 设置是否显示复制按钮
     * @param value true显示，false隐藏
     */
    suspend fun setShowCopyButton(value: Boolean) {
        context.dataStore.edit { it[keyShowCopyButton] = value }
    }

    /**
     * 设置是否显示编辑按钮
     * @param value true显示，false隐藏
     */
    suspend fun setShowEditButton(value: Boolean) {
        context.dataStore.edit { it[keyShowEditButton] = value }
    }

    /**
     * 设置是否显示取消固定按钮
     * @param value true显示，false隐藏
     */
    suspend fun setShowUnpinButton(value: Boolean) {
        context.dataStore.edit { it[keyShowUnpinButton] = value }
    }

    /**
     * 设置是否启用多行显示
     * @param value true启用，false单行
     */
    suspend fun setMultilineDisplay(value: Boolean) {
        context.dataStore.edit { it[keyMultilineDisplay] = value }
    }

    /**
     * 设置是否启用防删除保护
     * @param value true启用，false禁用
     */
    suspend fun setAntiDeleteProtection(value: Boolean) {
        context.dataStore.edit { it[keyAntiDeleteProtection] = value }
    }

    /**
     * 设置深色模式
     * @param value "system"跟随系统, "light"浅色, "dark"深色
     */
    suspend fun setDarkMode(value: String) {
        context.dataStore.edit { it[keyDarkMode] = value }
    }

    /**
     * 设置语言
     * @param value "system"跟随系统, "zh"中文, "en"英文
     */
    suspend fun setLanguage(value: String) {
        context.dataStore.edit { it[keyLanguage] = value }
    }
}
