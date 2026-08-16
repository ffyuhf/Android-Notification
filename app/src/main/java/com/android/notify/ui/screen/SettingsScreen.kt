package com.android.notify.ui.screen

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.android.notify.R
import com.android.notify.viewmodel.NotifyViewModel

/**
 * 设置页面
 *
 * 功能：
 * 1. 通知操作按钮开关（复制、编辑、取消固定）
 * 2. 多行显示开关
 * 3. 防删除保护开关
 * 4. 深色模式选择
 * 5. 语言选择（即时生效）
 * 6. 权限管理（通知权限 / 精确闹钟权限）
 *
 * 优化（2026-08-16）：
 * - B4 语言切换经 AppCompatDelegate.setApplicationLocales 即时生效
 * - U1 精确闹钟权限手动入口（Android 12+ 且未授权时显示）
 * - U2 通知权限状态展示与系统设置跳转（架构文档承诺但原未实现）
 *
 * 创建日期：2026-05-14 | 作者：Cline
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: NotifyViewModel) {
    val context = LocalContext.current

    // 收集设置状态
    val showCopyButton by viewModel.showCopyButton.collectAsState()
    val showEditButton by viewModel.showEditButton.collectAsState()
    val showUnpinButton by viewModel.showUnpinButton.collectAsState()
    val multilineDisplay by viewModel.multilineDisplay.collectAsState()
    val antiDeleteProtection by viewModel.antiDeleteProtection.collectAsState()
    val darkMode by viewModel.darkMode.collectAsState()
    val language by viewModel.language.collectAsState()

    // 权限状态（从系统设置页返回后刷新，U2）
    var notificationGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    var exactAlarmGranted by remember { mutableStateOf(hasExactAlarmPermission(context)) }
    LifecycleResumeEffect(Unit) {
        notificationGranted = hasNotificationPermission(context)
        exactAlarmGranted = hasExactAlarmPermission(context)
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // ===== 通知操作设置 =====
            SettingsSectionTitle(text = stringResource(R.string.settings_notification_actions))

            SettingsSwitchItem(
                title = stringResource(R.string.settings_show_copy_button),
                checked = showCopyButton,
                onCheckedChange = { viewModel.setShowCopyButton(it) }
            )

            SettingsSwitchItem(
                title = stringResource(R.string.settings_show_edit_button),
                checked = showEditButton,
                onCheckedChange = { viewModel.setShowEditButton(it) }
            )

            SettingsSwitchItem(
                title = stringResource(R.string.settings_show_unpin_button),
                checked = showUnpinButton,
                onCheckedChange = { viewModel.setShowUnpinButton(it) }
            )

            SettingsSwitchItem(
                title = stringResource(R.string.settings_multiline_display),
                checked = multilineDisplay,
                onCheckedChange = { viewModel.setMultilineDisplay(it) }
            )

            SettingsSwitchItem(
                title = stringResource(R.string.settings_anti_delete),
                subtitle = stringResource(R.string.settings_anti_delete_desc),
                checked = antiDeleteProtection,
                onCheckedChange = { viewModel.setAntiDeleteProtection(it) }
            )

            // ===== 外观设置 =====
            SettingsSectionTitle(text = stringResource(R.string.settings_appearance))

            // 深色模式三选一
            SettingsRadioGroup(
                title = stringResource(R.string.settings_dark_mode),
                options = listOf(
                    stringResource(R.string.dark_mode_system) to "system",
                    stringResource(R.string.dark_mode_light) to "light",
                    stringResource(R.string.dark_mode_dark) to "dark"
                ),
                selected = darkMode,
                onSelected = { viewModel.setDarkMode(it) }
            )

            // 语言三选一（切换即时生效，B4）
            SettingsRadioGroup(
                title = stringResource(R.string.settings_language),
                options = listOf(
                    stringResource(R.string.language_system) to "system",
                    stringResource(R.string.language_chinese) to "zh",
                    stringResource(R.string.language_english) to "en"
                ),
                selected = language,
                onSelected = { selected ->
                    viewModel.setLanguage(selected)
                    applyAppLanguage(selected)
                }
            )

            // ===== 权限管理（U1/U2） =====
            SettingsSectionTitle(text = stringResource(R.string.settings_permissions))

            // 通知权限条目
            SettingsPermissionItem(
                title = stringResource(R.string.settings_notification_permission),
                subtitle = stringResource(R.string.settings_notification_permission_desc),
                granted = notificationGranted,
                onRequest = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                    )
                }
            )

            // 精确闹钟权限条目：仅 Android 12+ 且未授权时显示（U1）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !exactAlarmGranted) {
                SettingsPermissionItem(
                    title = stringResource(R.string.settings_exact_alarm_permission),
                    subtitle = stringResource(R.string.settings_exact_alarm_permission_desc),
                    granted = exactAlarmGranted,
                    onRequest = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        )
                    }
                )
            }
        }
    }
}

/**
 * 应用语言切换（B4）
 *
 * 经 AppCompatDelegate per-app locale 机制即时生效并自动持久化：
 * API 33+ 由系统存储，以下版本由 appcompat 兼容存储。
 *
 * @param language 语言代码："system"跟随系统 / "zh"中文 / "en"英文
 */
private fun applyAppLanguage(language: String) {
    val locales = when (language) {
        "zh" -> LocaleListCompat.forLanguageTags("zh-CN")
        "en" -> LocaleListCompat.forLanguageTags("en")
        else -> LocaleListCompat.getEmptyLocaleList()
    }
    AppCompatDelegate.setApplicationLocales(locales)
}

/**
 * 检查通知权限状态（U2）
 *
 * @param context 上下文
 * @return true 已授权（Android 13 以下视为已授权）
 */
private fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * 检查精确闹针权限状态（U1）
 *
 * @param context 上下文
 * @return true 可调度精确闹钟（Android 12 以下视为已授权）
 */
private fun hasExactAlarmPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
}

/**
 * 设置分组标题
 */
@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

/**
 * 设置开关项
 */
@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    androidx.compose.material3.ListItem(
        headlineContent = { Text(title) },
        supportingContent = { subtitle?.let { Text(it) } },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    )
}

/**
 * 设置单选组
 */
@Composable
private fun SettingsRadioGroup(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )

    options.forEach { (label, value) ->
        androidx.compose.material3.ListItem(
            headlineContent = { Text(label) },
            leadingContent = {
                androidx.compose.material3.RadioButton(
                    selected = selected == value,
                    onClick = { onSelected(value) }
                )
            }
        )
    }
}

/**
 * 权限条目（U1/U2）
 *
 * @param title 权限名称
 * @param subtitle 权限用途说明
 * @param granted 当前授权状态
 * @param onRequest 点击授权按钮回调（跳转对应系统设置页）
 */
@Composable
private fun SettingsPermissionItem(
    title: String,
    subtitle: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    androidx.compose.material3.ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            if (granted) {
                Text(
                    text = stringResource(R.string.status_granted),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Button(onClick = onRequest) {
                    Text(stringResource(R.string.settings_grant_permission))
                }
            }
        }
    )
}
