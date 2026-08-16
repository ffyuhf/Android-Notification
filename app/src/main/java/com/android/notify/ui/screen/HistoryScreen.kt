package com.android.notify.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.notify.R
import com.android.notify.data.db.entity.NotificationEntity
import com.android.notify.viewmodel.NotifyViewModel
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 历史记录页面
 *
 * 功能：
 * 1. 显示所有通知历史记录列表（单列 / 两列网格可切换，H1）
 * 2. 每条记录可重新发送或删除（删除需二次确认，H2）
 * 3. 长按进入多选模式：全选 / 反选、批量删除 / 批量发送（H2）
 * 4. 顶栏搜索实时过滤标题与内容（H3）
 * 5. 清空全部功能（带确认对话框）
 *
 * 增强（2026-08-16 12:53 | H1/H2/H3）
 *
 * 创建日期：2026-05-14
 * 作者：Cline
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(viewModel: NotifyViewModel) {
    // ===== 数据流 =====
    val notifications by viewModel.filteredNotifications.collectAsState()
    val allNotifications by viewModel.allNotifications.collectAsState()
    val layoutMode by viewModel.historyLayoutMode.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isTwoColumn = layoutMode == "two_column"

    // ===== UI 状态 =====
    /** 是否处于搜索态（顶栏标题位替换为搜索框） */
    var isSearching by rememberSaveable { mutableStateOf(false) }
    /** 是否处于多选模式 */
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    /** 多选选中的记录ID集合（切页后清空属预期行为） */
    val selectedIds = remember { mutableStateMapOf<Int, Boolean>() }

    // ===== 对话框状态 =====
    /** 清空全部确认 */
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    /** 单条删除确认（H2 二级确认） */
    var pendingSingleDelete by remember { mutableStateOf<NotificationEntity?>(null) }
    /** 批量删除确认（H2） */
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }

    /** 进入多选模式并选中指定记录 */
    val enterSelection: (NotificationEntity) -> Unit = { entity ->
        selectionMode = true
        selectedIds[entity.id] = true
    }

    /** 切换指定记录的选中状态 */
    val toggleSelection: (NotificationEntity) -> Unit = { entity ->
        if (selectedIds[entity.id] == true) {
            selectedIds.remove(entity.id)
        } else {
            selectedIds[entity.id] = true
        }
    }

    /** 全选：选中当前过滤后可见的全部记录 */
    val selectAllVisible: () -> Unit = {
        notifications.forEach { selectedIds[it.id] = true }
    }

    /** 反选：对当前过滤后可见的记录取反（不可见记录的选中状态保持） */
    val invertVisibleSelection: () -> Unit = {
        notifications.forEach { entity ->
            if (selectedIds[entity.id] == true) {
                selectedIds.remove(entity.id)
            } else {
                selectedIds[entity.id] = true
            }
        }
    }

    /** 退出多选并清空选中 */
    val exitSelection: () -> Unit = {
        selectionMode = false
        selectedIds.clear()
    }

    Scaffold(
        topBar = {
            when {
                // ===== 多选模式顶栏（H2） =====
                selectionMode -> {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = exitSelection) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.history_exit_multi_select)
                                )
                            }
                        },
                        title = { Text(stringResource(R.string.history_selected_count, selectedIds.size)) },
                        actions = {
                            TextButton(onClick = selectAllVisible) {
                                Text(stringResource(R.string.history_select_all))
                            }
                            TextButton(onClick = invertVisibleSelection) {
                                Text(stringResource(R.string.history_invert_selection))
                            }
                        }
                    )
                }

                // ===== 搜索态顶栏（H3） =====
                isSearching -> {
                    TopAppBar(
                        title = {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = { Text(stringResource(R.string.history_search_hint)) },
                                singleLine = true,
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                            Icon(
                                                Icons.Default.Clear,
                                                contentDescription = stringResource(R.string.history_search_clear)
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        actions = {
                            IconButton(onClick = {
                                isSearching = false
                                viewModel.setSearchQuery("")
                            }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                            }
                        }
                    )
                }

                // ===== 普通顶栏 =====
                else -> {
                    TopAppBar(
                        title = { Text(stringResource(R.string.history_title)) },
                        actions = {
                            if (notifications.isNotEmpty()) {
                                // 搜索按钮（H3）
                                IconButton(onClick = { isSearching = true }) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = stringResource(R.string.history_search)
                                    )
                                }
                                // 布局切换按钮（H1）：图标显示目标模式，点击即切换
                                IconButton(onClick = {
                                    viewModel.setHistoryLayoutMode(
                                        if (isTwoColumn) "single" else "two_column"
                                    )
                                }) {
                                    Icon(
                                        imageVector = if (isTwoColumn) Icons.Default.ViewAgenda else Icons.Default.ViewWeek,
                                        contentDescription = stringResource(
                                            if (isTwoColumn) R.string.history_layout_single
                                            else R.string.history_layout_two_column
                                        )
                                    )
                                }
                                // 清空全部按钮
                                TextButton(onClick = { showDeleteAllDialog = true }) {
                                    Text(
                                        text = stringResource(R.string.btn_delete_all),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    )
                }
            }
        },
        bottomBar = {
            // ===== 多选批量操作栏（H2） =====
            if (selectionMode) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 批量发送：逐条按现有重发链路执行（D3）
                        OutlinedButton(
                            onClick = {
                                val entities = notifications.filter { selectedIds[it.id] == true }
                                if (entities.isNotEmpty()) {
                                    viewModel.resendNotifications(entities)
                                }
                                exitSelection()
                            },
                            enabled = selectedIds.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.history_send_selected))
                        }
                        // 批量删除：需二次确认（H2）
                        Button(
                            onClick = { showDeleteSelectedDialog = true },
                            enabled = selectedIds.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.history_delete_selected))
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        when {
            // 无任何记录
            allNotifications.isEmpty() -> {
                EmptyHistory(message = stringResource(R.string.history_empty), paddingValues = paddingValues)
            }

            // 有记录但搜索无匹配
            notifications.isEmpty() -> {
                EmptyHistory(message = stringResource(R.string.history_no_match), paddingValues = paddingValues)
            }

            // ===== 两列网格（H1） =====
            isTwoColumn -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    gridItems(notifications, key = { it.id }) { notification ->
                        CompactNotificationCard(
                            notification = notification,
                            selectionMode = selectionMode,
                            isSelected = selectedIds[notification.id] == true,
                            onItemClick = { if (selectionMode) toggleSelection(notification) },
                            onItemLongClick = { if (!selectionMode) enterSelection(notification) },
                            onResend = { viewModel.resendNotification(notification) },
                            onDelete = { pendingSingleDelete = notification }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }

            // ===== 单列列表 =====
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    items(notifications, key = { it.id }) { notification ->
                        NotificationHistoryItem(
                            notification = notification,
                            selectionMode = selectionMode,
                            isSelected = selectedIds[notification.id] == true,
                            onItemClick = { if (selectionMode) toggleSelection(notification) },
                            onItemLongClick = { if (!selectionMode) enterSelection(notification) },
                            onResend = { viewModel.resendNotification(notification) },
                            onDelete = { pendingSingleDelete = notification }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }
    }

    // ===== 单条删除二级确认（H2） =====
    pendingSingleDelete?.let { entity ->
        AlertDialog(
            onDismissRequest = { pendingSingleDelete = null },
            title = { Text(stringResource(R.string.btn_delete)) },
            text = { Text(stringResource(R.string.confirm_delete_single)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteNotification(entity.id)
                        pendingSingleDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSingleDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // ===== 批量删除二级确认（H2） =====
    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { Text(stringResource(R.string.history_delete_selected)) },
            text = { Text(stringResource(R.string.confirm_delete_selected, selectedIds.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteNotifications(selectedIds.keys.toList())
                        showDeleteSelectedDialog = false
                        exitSelection()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // ===== 清空全部确认（保留） =====
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text(stringResource(R.string.btn_delete_all)) },
            text = { Text(stringResource(R.string.confirm_delete)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllNotifications()
                        showDeleteAllDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * 空状态提示
 *
 * @param message 空态文案（暂无记录 / 无匹配结果）
 * @param paddingValues Scaffold 内边距
 */
@Composable
private fun EmptyHistory(message: String, paddingValues: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 单条历史记录卡片（单列模式）
 *
 * 增强（2026-08-16 | H2）：长按进入多选；多选模式下点击切换选中并显示勾选框，
 * 隐藏操作按钮避免误触。
 *
 * @param notification 通知实体
 * @param selectionMode 是否处于多选模式
 * @param isSelected 当前记录是否被选中
 * @param onItemClick 点击回调（多选模式下切换选中）
 * @param onItemLongClick 长按回调（进入多选并选中）
 * @param onResend 重新发送回调
 * @param onDelete 删除回调
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotificationHistoryItem(
    notification: NotificationEntity,
    selectionMode: Boolean,
    isSelected: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    onResend: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onItemLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    Checkbox(checked = isSelected, onCheckedChange = { onItemClick() })
                }
                Column(modifier = Modifier.weight(1f)) {
                    // 标题行
                    if (!notification.title.isNullOrBlank()) {
                        Text(
                            text = notification.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    // 内容
                    Text(
                        text = notification.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 底部信息行：时间 + 状态 + 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 时间和状态信息
                Column {
                    Text(
                        text = formatHistoryTime(notification.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatStatusText(notification),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (notification.isActive && notification.isPinned)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 操作按钮（多选模式下隐藏避免误触）
                if (!selectionMode) {
                    Row {
                        // 重新发送
                        IconButton(onClick = onResend) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = stringResource(R.string.btn_resend),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        // 删除
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.btn_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 双列紧凑历史记录卡片（H1 新增，两列网格模式）
 *
 * 信息精简排布：标题1行 + 内容2行 + 时间/状态 + 操作小图标。
 * 多选模式下整卡点击切换选中并显示勾选框，隐藏操作按钮。
 *
 * @param notification 通知实体
 * @param selectionMode 是否处于多选模式
 * @param isSelected 当前记录是否被选中
 * @param onItemClick 点击回调（多选模式下切换选中）
 * @param onItemLongClick 长按回调（进入多选并选中）
 * @param onResend 重新发送回调
 * @param onDelete 删除回调
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactNotificationCard(
    notification: NotificationEntity,
    selectionMode: Boolean,
    isSelected: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    onResend: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onItemLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    Checkbox(checked = isSelected, onCheckedChange = { onItemClick() })
                }
                Column(modifier = Modifier.weight(1f)) {
                    // 标题（可选，1行省略）
                    if (!notification.title.isNullOrBlank()) {
                        Text(
                            text = notification.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    // 内容摘要（2行省略）
                    Text(
                        text = notification.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 底部：时间/状态 + 操作小图标
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatHistoryTime(notification.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatStatusText(notification),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (notification.isActive && notification.isPinned)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 操作小图标（多选模式下隐藏避免误触）
                if (!selectionMode) {
                    Row {
                        IconButton(onClick = onResend, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = stringResource(R.string.btn_resend),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.btn_delete),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 格式化历史记录时间显示
 */
private fun formatHistoryTime(timeMillis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(timeMillis)
}

/**
 * 格式化定时时间显示
 */
private fun formatScheduledTime(timeMillis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(timeMillis)
}

/**
 * 记录状态文本：未触发定时 / 已固定 / 普通
 */
private fun formatStatusText(notification: NotificationEntity): String {
    return when {
        !notification.isActive ->
            if (notification.scheduledAt != null) "⏰ ${formatScheduledTime(notification.scheduledAt)}" else "○"
        notification.isPinned -> "●"
        else -> "○"
    }
}
