package com.android.notify.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
 * 5. 语言选择
 *
 * 创建日期：2026-05-14
 * 作者：Cline
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: NotifyViewModel) {
    // 收集设置状态
    val showCopyButton by viewModel.showCopyButton.collectAsState()
    val showEditButton by viewModel.showEditButton.collectAsState()
    val showUnpinButton by viewModel.showUnpinButton.collectAsState()
    val multilineDisplay by viewModel.multilineDisplay.collectAsState()
    val antiDeleteProtection by viewModel.antiDeleteProtection.collectAsState()
    val darkMode by viewModel.darkMode.collectAsState()
    val language by viewModel.language.collectAsState()

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

            // 语言三选一
            SettingsRadioGroup(
                title = stringResource(R.string.settings_language),
                options = listOf(
                    stringResource(R.string.language_system) to "system",
                    stringResource(R.string.language_chinese) to "zh",
                    stringResource(R.string.language_english) to "en"
                ),
                selected = language,
                onSelected = { viewModel.setLanguage(it) }
            )
        }
    }
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
