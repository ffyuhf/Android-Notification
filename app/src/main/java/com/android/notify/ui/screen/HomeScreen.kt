package com.android.notify.ui.screen

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.remember
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
 * 创建日期：2026-05-14
 * 作者：Cline
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: NotifyViewModel) {
    val context = LocalContext.current

    // 输入状态
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    // 定时发送状态
    var showScheduleSection by remember { mutableStateOf(false) }
    var scheduledTime by remember { mutableLongStateOf(0L) }
    var scheduledTimeText by remember { mutableStateOf("") }
    var repeatType by remember { mutableStateOf<String?>(null) }
    var repeatExpanded by remember { mutableStateOf(false) }

    // 日期时间选择器
    val calendar = remember { Calendar.getInstance() }
    val datePickerDialog = remember {
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
                        scheduledTime = calendar.timeInMillis
                        scheduledTimeText = SimpleDateFormat(
                            "yyyy-MM-dd HH:mm", Locale.getDefault()
                        ).format(calendar.time)
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

            // 确认定时发送按钮
            Button(
                onClick = {
                    if (content.isNotBlank() && scheduledTime > 0) {
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
                enabled = content.isNotBlank() && scheduledTime > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(stringResource(R.string.confirm))
            }
        }
    }
}
