package com.android.notify.ui.screen

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.notify.R
import com.android.notify.viewmodel.NotifyViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 首页 - 创建通知
 *
 * 功能：
 * 1. 输入通知标题和内容
 * 2. 即时发送按钮
 * 3. 定时发送（日期时间选择器 + 重复类型）
 *
 * 优化（2026-08-16）：
 * - U1 精确闹钟权限惰性引导：定时确认时检查权限，无权限才提示并跳转设置页
 * - U3 输入状态 rememberSaveable：切换Tab后输入内容保留
 * - U4 定时时间未来校验：过去时间不可提交并提示
 *
 * 创建日期：2026-05-14 | 作者：Cline
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: NotifyViewModel) {
    val context = LocalContext.current

    // 输入状态（rememberSaveable：切Tab后保留，U3）
    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }

    // 定时发送状态（rememberSaveable：切Tab后保留，U3）
    var showScheduleSection by rememberSaveable { mutableStateOf(false) }
    var scheduledTime by rememberSaveable { mutableLongStateOf(0L) }
    var scheduledTimeText by rememberSaveable { mutableStateOf("") }
    var repeatType by rememberSaveable { mutableStateOf<String?>(null) }
    var repeatExpanded by rememberSaveable { mutableStateOf(false) }

    // 定时时间未来校验（U4）：已选择且不晚于当前时间视为无效
    val isScheduledTimeValid = scheduledTime > System.currentTimeMillis()
    val showPastTimeError = scheduledTime > 0L && !isScheduledTimeValid

    // 日期时间选择器（选择过程临时状态，无需保存）
    val calendar = rememberSafeCalendar()
    val datePickerDialog = rememberDatePicker(context, calendar) { timeMillis, timeText ->
        scheduledTime = timeMillis
        scheduledTimeText = timeText
    }

    // 重复类型选项
    val repeatOptions = listOf(
        stringResource(R.string.repeat_none) to null,
        stringResource(R.string.repeat_hourly) to "hourly",
        stringResource(R.string.repeat_daily) to "daily",
        stringResource(R.string.repeat_weekly) to "weekly",
        stringResource(R.string.repeat_monthly) to "monthly",
        stringResource(R.string.repeat_yearly) to "yearly"
    )
    val repeatLabel = repeatOptions.find { it.second == repeatType }?.first
        ?: stringResource(R.string.repeat_none)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 页面标题
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        // 标题输入框（可选）
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.hint_notification_title)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )

        // 内容输入框
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text(stringResource(R.string.hint_notification_content)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            shape = MaterialTheme.shapes.medium
        )

        // 操作按钮行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 立即发送按钮
            Button(
                onClick = {
                    if (content.isNotBlank()) {
                        viewModel.sendNow(
                            title = title.ifBlank { null },
                            content = content
                        )
                        // 清空输入
                        content = ""
                        title = ""
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = content.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text(stringResource(R.string.btn_send_now))
            }

            // 定时发送按钮
            OutlinedButton(
                onClick = { showScheduleSection = !showScheduleSection },
                modifier = Modifier.weight(1f),
                enabled = content.isNotBlank()
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text(stringResource(R.string.btn_schedule))
            }
        }

        // 定时发送设置区域
        if (showScheduleSection) {
            Spacer(modifier = Modifier.height(8.dp))

            // 日期时间选择
            OutlinedButton(
                onClick = { datePickerDialog.show() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (scheduledTimeText.isNotBlank()) scheduledTimeText
                    else stringResource(R.string.select_date_time)
                )
            }

            // 定时时间无效提示（U4）
            if (showPastTimeError) {
                Text(
                    text = stringResource(R.string.error_schedule_past_time),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // 重复类型选择
            ExposedDropdownMenuBox(
                expanded = repeatExpanded,
                onExpandedChange = { repeatExpanded = !repeatExpanded }
            ) {
                OutlinedTextField(
                    value = repeatLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.label_repeat)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = repeatExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )

                ExposedDropdownMenu(
                    expanded = repeatExpanded,
                    onDismissRequest = { repeatExpanded = false }
                ) {
                    repeatOptions.forEach { (label, value) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                repeatType = value
                                repeatExpanded = false
                            }
                        )
                    }
                }
            }

            // 确认定时发送按钮（时间必须在未来，U4）
            Button(
                onClick = {
                    // 精确闹钟权限惰性检查（U1）：无权限时提示并引导，不发送
                    if (!ensureExactAlarmPermission(context)) return@Button

                    if (content.isNotBlank() && isScheduledTimeValid) {
                        viewModel.scheduleNotification(
                            title = title.ifBlank { null },
                            content = content,
                            scheduledAt = scheduledTime,
                            repeatType = repeatType
                        )
                        // 清空输入
                        content = ""
                        title = ""
                        scheduledTime = 0L
                        scheduledTimeText = ""
                        repeatType = null
                        showScheduleSection = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = content.isNotBlank() && isScheduledTimeValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(stringResource(R.string.confirm))
            }
        }
    }
}

/**
 * 精确闹钟权限惰性检查（U1）
 *
 * SCHEDULE_EXACT_ALARM 属特殊访问权限（跳设置页授权），
 * 仅在用户发起定时操作且权限缺失时才引导，避免启动即强跳设置页。
 *
 * @param context 上下文
 * @return true 已具备精确闹钟能力可继续发送；false 已引导用户跳转设置页
 */
private fun ensureExactAlarmPermission(context: Context): Boolean {
    // Android 12 以下无需该权限
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true

    val alarmManager = context.getSystemService(AlarmManager::class.java)
    if (alarmManager.canScheduleExactAlarms()) return true

    // 无权限：提示并跳转系统精确闹钟设置页
    Toast.makeText(context, R.string.permission_exact_alarm_needed, Toast.LENGTH_LONG).show()
    context.startActivity(
        Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    )
    return false
}

/**
 * 提供选择器基准日历（remember 临时状态）
 */
@Composable
private fun rememberSafeCalendar(): Calendar = androidx.compose.runtime.remember { Calendar.getInstance() }

/**
 * 创建日期时间级联选择器
 *
 * @param context 上下文
 * @param calendar 基准日历
 * @param onTimeSelected 选择完成回调（时间戳 + 格式化文本）
 */
@Composable
private fun rememberDatePicker(
    context: Context,
    calendar: Calendar,
    onTimeSelected: (Long, String) -> Unit
): DatePickerDialog {
    return androidx.compose.runtime.remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                // 日期选择后弹出时间选择器
                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        calendar.set(Calendar.MINUTE, minute)
                        calendar.set(Calendar.SECOND, 0)
                        onTimeSelected(
                            calendar.timeInMillis,
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(calendar.time)
                        )
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }
}
