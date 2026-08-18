package com.android.notify.ui.screen

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.notify.R
import com.android.notify.ui.component.AdaptiveImage
import com.android.notify.util.ImageStorageHelper
import com.android.notify.viewmodel.NotifyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 首页 - 创建通知
 *
 * 功能：
 * 1. 输入通知标题和内容
 * 2. 即时发送按钮
 * 3. 定时发送（ModalBottomSheet + MD3 日期时间选择器 + 重复类型）
 *
 * MD3 重绘（2026-08-18 15:54 | 界面MD3全面重绘）：
 * - P1 增设 TopAppBar 与历史/设置页统一顶部骨架，移除大标题文字与 statusBarsPadding
 *   手动补偿（顶栏自带状态栏 inset）
 * - P2 定时设置由内联条件展开区块改为 ModalBottomSheet，消除与操作按钮行的层级混乱
 * - P3 View 体系 DatePickerDialog/TimePickerDialog 替换为 material3 DatePickerDialog +
 *   TimePicker（AlertDialog 包装），风格统一
 *
 * 逻辑保持（未变更）：
 * - U1 精确闹钟权限惰性引导：定时确认时检查权限，无权限才提示并跳转设置页
 * - U3 输入状态 rememberSaveable：切换Tab后输入内容保留
 * - U4 定时时间未来校验：过去时间不可提交并提示
 * - 纯图通知支持：正文与图片至少一项即可发送
 * - 图片完整显示契约：预览经 AdaptiveImage 自适应完整显示（限高 320dp）
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

    // 图片路径状态：保存复制到私有目录后的绝对路径（rememberSaveable 持久化，
    // Photo Picker 返回的 Uri 为临时授权不可跨进程保存）
    var imagePath by rememberSaveable { mutableStateOf<String?>(null) }

    // 发送/定时输入合法性：正文与图片至少一项（纯图通知支持）
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

    // ===== 定时发送状态（rememberSaveable：切Tab后保留，U3）=====
    /** 定时设置 BottomSheet 显隐（MD3 重绘：原内联展开区改 Sheet 承载） */
    var showScheduleSheet by rememberSaveable { mutableStateOf(false) }
    var scheduledTime by rememberSaveable { mutableLongStateOf(0L) }
    var scheduledTimeText by rememberSaveable { mutableStateOf("") }
    var repeatType by rememberSaveable { mutableStateOf<String?>(null) }

    // 定时时间未来校验（U4）：已选择且不晚于当前时间视为无效
    val isScheduledTimeValid = scheduledTime > System.currentTimeMillis()
    val showPastTimeError = scheduledTime > 0L && !isScheduledTimeValid

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

    // ===== 统一骨架：TopAppBar 与历史/设置页一致（P1）=====
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        // ===== 表单分区：输入区 → 图片区 → 操作区 =====
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题输入框（可选）
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.hint_notification_title)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            // 内容输入框（heightIn 保底可视面积，输入更多内容时按钮随滚动可达）
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text(stringResource(R.string.hint_notification_content)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp),
                shape = MaterialTheme.shapes.medium
            )

            // ===== 图片选择区 =====
            if (imagePath != null) {
                // 已选图片：预览（AdaptiveImage 契约：按图比例自适应完整显示，限高 320dp）
                AdaptiveImage(
                    path = imagePath,
                    maxHeight = 320.dp,
                    cornerRadius = 12.dp,
                    contentDescription = stringResource(R.string.action_add_image),
                    decodeMaxDimension = 800
                )
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

            // ===== 操作区：主操作 Filled + 次操作 Tonal（MD3 按钮层级）=====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 立即发送（主操作，校验：正文与图片至少一项）
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
                    enabled = isValidInput
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(stringResource(R.string.btn_send_now))
                }

                // 定时发送（次操作：打开 BottomSheet 配置定时，P2）
                FilledTonalButton(
                    onClick = { showScheduleSheet = true },
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
        }
    }

    // ===== 定时发送 ModalBottomSheet（P2：原内联展开区迁入，逻辑不变）=====
    if (showScheduleSheet) {
        ScheduleBottomSheet(
            scheduledTimeText = scheduledTimeText,
            isScheduledTimeValid = isScheduledTimeValid,
            showPastTimeError = showPastTimeError,
            repeatOptions = repeatOptions,
            repeatLabel = repeatLabel,
            onRepeatTypeChange = { repeatType = it },
            onTimeSelected = { timeMillis, timeText ->
                scheduledTime = timeMillis
                scheduledTimeText = timeText
            },
            onConfirm = {
                // 精确闹钟权限惰性检查（U1）：具备权限且输入合法才提交定时
                if (ensureExactAlarmPermission(context) && isValidInput && isScheduledTimeValid) {
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
                    showScheduleSheet = false
                }
            },
            onDismiss = { showScheduleSheet = false }
        )
    }
}

/**
 * 定时发送设置 BottomSheet（MD3 重绘 2026-08-18 | P2/P3）
 *
 * 结构：日期时间选择（MD3 DatePickerDialog → TimePicker 级联）+ 重复类型下拉 + 确认按钮。
 * 原 View 体系日期时间对话框整体替换为 material3 组件，选择流程保持级联等价。
 *
 * @param scheduledTimeText 已选定时时间显示文本（空串表示未选）
 * @param isScheduledTimeValid 定时时间是否在未来（U4）
 * @param showPastTimeError 是否显示过去时间错误提示
 * @param repeatOptions 重复类型选项（文案 → 类型值，null = 不重复）
 * @param repeatLabel 当前重复类型显示文案
 * @param onRepeatTypeChange 重复类型变更回调
 * @param onTimeSelected 日期时间选择完成回调（时间戳 + 格式化文本）
 * @param onConfirm 确认定时发送回调（含权限引导与输入校验）
 * @param onDismiss 关闭 Sheet 回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleBottomSheet(
    scheduledTimeText: String,
    isScheduledTimeValid: Boolean,
    showPastTimeError: Boolean,
    repeatOptions: List<Pair<String, String?>>,
    repeatLabel: String,
    onRepeatTypeChange: (String?) -> Unit,
    onTimeSelected: (Long, String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // 日期选择状态：初始选中当天（MD3 DatePicker）
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis()
    )
    // 时间选择状态：默认当前时刻（MD3 TimePicker，24 小时制）
    val timePickerState = rememberTimePickerState(is24Hour = true)

    /** 日期选择对话框显隐（级联第一步） */
    var showDatePicker by remember { mutableStateOf(false) }
    /** 时间选择对话框显隐（级联第二步：日期确认后自动打开） */
    var showTimePicker by remember { mutableStateOf(false) }
    /** 暂存日期选择结果（UTC 当日零点毫秒），时间确认后组合为完整时间戳 */
    var pendingDateMillis by remember { mutableStateOf<Long?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sheet 标题
            Text(
                text = stringResource(R.string.btn_schedule),
                style = MaterialTheme.typography.titleLarge
            )

            // 日期时间选择：已选显示所选时间，未选显示引导文案
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
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

            // 重复类型选择（MD3 ExposedDropdownMenu）
            var repeatExpanded by rememberSaveable { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = repeatExpanded,
                onExpandedChange = { repeatExpanded = !repeatExpanded }
            ) {
                OutlinedTextField(
                    value = repeatLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.label_repeat)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = repeatExpanded)
                    },
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
                                onRepeatTypeChange(value)
                                repeatExpanded = false
                            }
                        )
                    }
                }
            }

            // 确认按钮（时间必须已选且在未来，U4）
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                enabled = scheduledTimeText.isNotBlank() && isScheduledTimeValid
            ) {
                Text(stringResource(R.string.confirm))
            }
        }
    }

    // ===== 级联第一步：MD3 日期选择对话框（替换 View 体系 DatePickerDialog）=====
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 暂存所选日期（UTC 当日零点），进入时间选择
                        pendingDateMillis = datePickerState.selectedDateMillis
                        showDatePicker = false
                        showTimePicker = true
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // ===== 级联第二步：MD3 时间选择（AlertDialog 包装 TimePicker，material3 无独立对话框组件）=====
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.select_time)) },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 组合日期（UTC 零点转本地日期分量）+ 时间（本地时分）为完整时间戳
                        pendingDateMillis?.let { utcMillis ->
                            val calendar = utcMillisToLocalCalendar(utcMillis).apply {
                                set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                                set(Calendar.MINUTE, timePickerState.minute)
                                set(Calendar.SECOND, 0)
                            }
                            onTimeSelected(
                                calendar.timeInMillis,
                                SimpleDateFormat(
                                    "yyyy-MM-dd HH:mm", Locale.getDefault()
                                ).format(calendar.time)
                            )
                        }
                        showTimePicker = false
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * DatePicker 选择毫秒（UTC 当日零点）转本地时区日历
 *
 * DatePicker 返回值为 UTC 当天 00:00，直接按毫秒设入本地日历会因时差偏移日期；
 * 此处仅取年月日分量重建本地日历，保证日期语义一致。
 *
 * @param utcMillis DatePicker 选中的 UTC 毫秒值
 * @return 只含所选日期（本地时区）的日历，时分秒为当前值待上层覆盖
 */
private fun utcMillisToLocalCalendar(utcMillis: Long): Calendar {
    val utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = utcMillis
    }
    return Calendar.getInstance().apply {
        set(
            utcCalendar.get(Calendar.YEAR),
            utcCalendar.get(Calendar.MONTH),
            utcCalendar.get(Calendar.DAY_OF_MONTH)
        )
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
    android.widget.Toast.makeText(
        context, R.string.permission_exact_alarm_needed, android.widget.Toast.LENGTH_LONG
    ).show()
    context.startActivity(
        Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    )
    return false
}
