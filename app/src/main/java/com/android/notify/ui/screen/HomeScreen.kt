package com.android.notify.ui.screen

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.notify.R
import com.android.notify.util.AsyncBitmap
import com.android.notify.util.ImageStorageHelper
import com.android.notify.viewmodel.NotifyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 * - F6 根布局补 statusBarsPadding（外层 Scaffold inset 清零后保持顶部留白）
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

    // 图片路径状态（新增 2026-08-16 | 图片通知）：
    // 保存复制到私有目录后的绝对路径字符串（rememberSaveable 持久化，
    // Photo Picker 返回的 Uri 为临时授权不可跨进程保存）
    var imagePath by rememberSaveable { mutableStateOf<String?>(null) }

    // 发送/定时输入合法性：正文与图片至少一项（纯图通知支持，v1.1 决策）
    val isValidInput = content.isNotBlank() || imagePath != null

    // 图片选择协程作用域（复制文件为 IO 操作，不阻塞主线程）
    val scope = rememberCoroutineScope()

    // 系统照片选择器（PickVisualMedia，零运行时权限）
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val newPath = withContext(Dispatchers.IO) {
                    ImageStorageHelper.copyToPrivateStorage(context, uri)
                }
                if (newPath != null) {
                    // 替换旧图：删除尚未发送的旧图片文件，防私有目录孤儿残留
                    val oldPath = imagePath
                    if (oldPath != null && oldPath != newPath) {
                        ImageStorageHelper.deleteImage(oldPath)
                    }
                    imagePath = newPath
                }
            }
        }
    }

    /** 移除已选图片（同步删除私有目录文件） */
    val removeImage: () -> Unit = {
        ImageStorageHelper.deleteImage(imagePath)
        imagePath = null
    }

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
            // F6（2026-08-16 13:56）：外层 Scaffold inset 清零后，
            // 本页无 TopAppBar，需自行补状态栏留白
            .statusBarsPadding()
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

        // 图片选择区域（新增 2026-08-16 | 图片通知）
        if (imagePath != null) {
            // 已选图片：预览缩略图 + 移除按钮
            // 修正（2026-08-16 15:39 | 图片通知闪退修复）：解码改异步，
            // 原主线程 remember 同步解码大图时叠加发送链路解码易 OOM 闪退
            val previewBitmap by AsyncBitmap.rememberSampledBitmap(imagePath, 800)
            previewBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = stringResource(R.string.action_add_image),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            TextButton(
                onClick = removeImage,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.action_remove_image))
            }
        } else {
            // 未选图片：添加入口（系统照片选择器，零权限）
            OutlinedButton(
                onClick = {
                    imagePickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text(stringResource(R.string.action_add_image))
            }
        }

        // 操作按钮行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 立即发送按钮（校验：正文与图片至少一项）
            Button(
                onClick = {
                    if (isValidInput) {
                        viewModel.sendNow(
                            title = title.ifBlank { null },
                            content = content,
                            imagePath = imagePath
                        )
                        // 清空输入（图片文件由数据库记录引用，此处仅清 UI 状态）
                        content = ""
                        title = ""
                        imagePath = null
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = isValidInput,
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
                enabled = isValidInput
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

            // 确认定时发送按钮（时间必须在未来，U4；正文与图片至少一项）
            Button(
                onClick = {
                    // 精确闹钟权限惰性检查（U1）：无权限时提示并引导，不发送
                    if (!ensureExactAlarmPermission(context)) return@Button

                    if (isValidInput && isScheduledTimeValid) {
                        viewModel.scheduleNotification(
                            title = title.ifBlank { null },
                            content = content,
                            imagePath = imagePath,
                            scheduledAt = scheduledTime,
                            repeatType = repeatType
                        )
                        // 清空输入（图片文件由数据库记录引用，此处仅清 UI 状态）
                        content = ""
                        title = ""
                        imagePath = null
                        scheduledTime = 0L
                        scheduledTimeText = ""
                        repeatType = null
                        showScheduleSection = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isValidInput && isScheduledTimeValid,
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
