package com.android.notify.ui.screen

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ContentCopy
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.android.notify.R
import com.android.notify.util.AppLogger
import com.android.notify.viewmodel.NotifyViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 设置页面
 *
 * 功能：
 * 1. 通知操作按钮开关（复制、编辑、取消固定）
 * 2. 多行显示 / 防删除保护 / 重发响铃开关
 * 3. 深色模式 / 语言选择（即时生效）
 * 4. 权限管理（通知权限 / 精确闹钟权限）
 * 5. 日志分级别导出
 *
 * MD3 重绘（2026-08-18 15:59 | 界面MD3全面重绘）：
 * - P7 深色模式/语言 RadioButton 长列表改为 SingleChoiceSegmentedButtonRow
 *   分段按钮（MD3 单选惯例，压缩纵向占用）
 * - P8 分组重排：MD3 设置页惯例（分组标题 primary 色通栏 + ListItem 通栏 +
 *   组间分隔线），开关项增加语义图标锚点；顶栏配色与三页统一
 *
 * 逻辑保持（未变更）：B4 语言切换经 AppCompatDelegate 即时生效；
 * U1 精确闹钟权限手动入口；U2 通知权限状态展示；权限返回自动刷新；
 * 日志导出（级别对话框 → SAF → Toast）。
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
    val resendSoundEnabled by viewModel.resendSoundEnabled.collectAsState()
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

    // ===== 日志导出状态 =====
    /** 级别选择对话框显隐 */
    var showLogLevelDialog by remember { mutableStateOf(false) }
    /** 待导出的最低级别（对话框确认后回填，launcher 回调时消费） */
    var exportMinLevel by remember { mutableStateOf<AppLogger.LogLevel?>(null) }

    // SAF 文档创建 launcher（零运行时权限）：用户选定保存位置后回调目标 URI
    val exportLogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            viewModel.exportLog(uri, exportMinLevel)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                // MD3 设置页惯例：ListItem 通栏无水平 padding，滚动防内容超屏不可达（F1）
                .verticalScroll(rememberScrollState())
        ) {
            // ===== 通知操作设置 =====
            SettingsSectionTitle(text = stringResource(R.string.settings_notification_actions))

            SettingsSwitchItem(
                title = stringResource(R.string.settings_show_copy_button),
                icon = Icons.AutoMirrored.Filled.ContentCopy,
                checked = showCopyButton,
                onCheckedChange = { viewModel.setShowCopyButton(it) }
            )

            SettingsSwitchItem(
                title = stringResource(R.string.settings_show_edit_button),
                icon = Icons.Default.Edit,
                checked = showEditButton,
                onCheckedChange = { viewModel.setShowEditButton(it) }
            )

            SettingsSwitchItem(
                title = stringResource(R.string.settings_show_unpin_button),
                icon = Icons.Default.PushPin,
                checked = showUnpinButton,
                onCheckedChange = { viewModel.setShowUnpinButton(it) }
            )

            SettingsSwitchItem(
                title = stringResource(R.string.settings_multiline_display),
                icon = Icons.Default.FormatLineSpacing,
                checked = multilineDisplay,
                onCheckedChange = { viewModel.setMultilineDisplay(it) }
            )

            SettingsSwitchItem(
                title = stringResource(R.string.settings_anti_delete),
                subtitle = stringResource(R.string.settings_anti_delete_desc),
                icon = Icons.Default.Shield,
                checked = antiDeleteProtection,
                onCheckedChange = { viewModel.setAntiDeleteProtection(it) }
            )

            SettingsSwitchItem(
                title = stringResource(R.string.settings_resend_sound),
                subtitle = stringResource(R.string.settings_resend_sound_desc),
                icon = Icons.Default.NotificationsActive,
                checked = resendSoundEnabled,
                onCheckedChange = { viewModel.setResendSoundEnabled(it) }
            )

            SettingsSectionDivider()

            // ===== 外观设置 =====
            SettingsSectionTitle(text = stringResource(R.string.settings_appearance))

            // 深色模式三选一（MD3 重绘 P7：分段按钮）
            SettingsChoiceGroup(
                title = stringResource(R.string.settings_dark_mode),
                icon = Icons.Default.DarkMode,
                options = listOf(
                    stringResource(R.string.dark_mode_system) to "system",
                    stringResource(R.string.dark_mode_light) to "light",
                    stringResource(R.string.dark_mode_dark) to "dark"
                ),
                selected = darkMode,
                onSelected = { viewModel.setDarkMode(it) }
            )

            // 语言三选一（切换即时生效，B4）
            SettingsChoiceGroup(
                title = stringResource(R.string.settings_language),
                icon = Icons.Default.Language,
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

            SettingsSectionDivider()

            // ===== 权限管理（U1/U2）=====
            SettingsSectionTitle(text = stringResource(R.string.settings_permissions))

            // 通知权限条目
            SettingsPermissionItem(
                title = stringResource(R.string.settings_notification_permission),
                subtitle = stringResource(R.string.settings_notification_permission_desc),
                icon = Icons.Default.Notifications,
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
                    icon = Icons.Default.Alarm,
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

            SettingsSectionDivider()

            // ===== 日志 =====
            SettingsSectionTitle(text = stringResource(R.string.settings_log_section))

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_export_log)) },
                supportingContent = { Text(stringResource(R.string.settings_export_log_desc)) },
                leadingContent = {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = {
                    OutlinedButton(onClick = { showLogLevelDialog = true }) {
                        Text(stringResource(R.string.settings_export_log_action))
                    }
                }
            )
        }
    }

    // ===== 级别选择对话框：全部 / INFO及以上 / WARN及以上 / 仅ERROR =====
    if (showLogLevelDialog) {
        // 对话框内当前选中项索引（默认"全部"）
        var selectedLevelIndex by remember { mutableIntStateOf(0) }
        AlertDialog(
            onDismissRequest = { showLogLevelDialog = false },
            title = { Text(stringResource(R.string.settings_export_log)) },
            text = {
                Column {
                    logLevelChoices.forEachIndexed { index, (labelRes, _) ->
                        ListItem(
                            headlineContent = { Text(stringResource(labelRes)) },
                            leadingContent = {
                                RadioButton(
                                    selected = selectedLevelIndex == index,
                                    onClick = { selectedLevelIndex = index }
                                )
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogLevelDialog = false
                        exportMinLevel = logLevelChoices[selectedLevelIndex].second
                        exportLogLauncher.launch(defaultLogFileName())
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogLevelDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/** 导出级别选项：文案资源 → 最低级别（null = 全部导出） */
private val logLevelChoices: List<Pair<Int, AppLogger.LogLevel?>> = listOf(
    R.string.log_level_all to null,
    R.string.log_level_info_above to AppLogger.LogLevel.INFO,
    R.string.log_level_warn_above to AppLogger.LogLevel.WARN,
    R.string.log_level_error_only to AppLogger.LogLevel.ERROR
)

/**
 * 生成导出日志默认文件名：notify_log_日期_时间.txt
 *
 * @return 形如 notify_log_20260816_153900.txt 的文件名
 */
private fun defaultLogFileName(): String {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return "notify_log_$timestamp.txt"
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
 * 设置分组标题（MD3 重绘 P8：primary 色标题通栏分组惯例）
 *
 * @param text 分组标题文案
 */
@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

/**
 * 设置分组间分隔线（MD3 重绘 P8：组间留白 + 细分隔）
 */
@Composable
private fun SettingsSectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

/**
 * 设置开关项（MD3 重绘 P8：增加语义图标锚点）
 *
 * @param title 开关标题
 * @param subtitle 可选副标题（用途说明）
 * @param icon 语义图标
 * @param checked 当前开关状态
 * @param onCheckedChange 开关切换回调
 */
@Composable
private fun SettingsSwitchItem(
    title: String,
    icon: ImageVector,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { subtitle?.let { Text(it) } },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    )
}

/**
 * 设置单选分组（MD3 重绘 P7：RadioButton 列表改分段按钮）
 *
 * @param title 分组标题
 * @param icon 语义图标
 * @param options 选项列表（文案 → 值）
 * @param selected 当前选中值
 * @param onSelected 选中回调
 */
@Composable
private fun SettingsChoiceGroup(
    title: String,
    icon: ImageVector,
    options: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit
) {
    // 标题行：图标 + 标题
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }

    // 单选分段按钮（MD3）：中文标签单行显示
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        options.forEachIndexed { index, (label, value) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelected(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = {
                    Text(
                        text = label,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )
        }
    }
}

/**
 * 权限条目（U1/U2，MD3 重绘 P8：增加语义图标）
 *
 * @param title 权限名称
 * @param subtitle 权限用途说明
 * @param icon 语义图标
 * @param granted 当前授权状态
 * @param onRequest 点击授权按钮回调（跳转对应系统设置页）
 */
@Composable
private fun SettingsPermissionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    granted: Boolean,
    onRequest: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
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
