package com.android.notify.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.automirrored.filled.Send
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
 *
 * 修正（2026-08-16 13:30 | F1-F5）：
 * F1 两列网格 Spacer item 占格导致左上空白/首条靠右/非Z字形 → 改 contentPadding
 * F2 顶栏三态切换 AnimatedContent 淡入+滑动动画
 * F3 布局切换图标改为跟随当前模式
 * F4 搜索入口移至"历史记录"标题旁
 * F5 移除顶栏"清空全部"按钮（清空走"长按→全选→删除选中"）
 *
 * 修正（2026-08-16 13:56 | F6-F8）：
 * F6 顶栏上方空白根因 = 嵌套 Scaffold 状态栏 inset 双算（MainActivity 外层
 *    contentWindowInsets 清零后自动消除，本文件无列表改动）
 * F7 普通/搜索顶栏合并为单 TopAppBar，搜索框从按钮位置 expandHorizontally 展开
 * F8 搜索图标恢复 24dp 与"历史记录"标题视觉等大
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
            // ===== 双态顶栏（F2/F7 修正 2026-08-16 13:56）：
            // 多选态与普通/搜索态的整体切换用 AnimatedContent 淡入+滑动过渡；
            // 普通↔搜索 态合并为单 TopAppBar，title 内 AnimatedVisibility
            // 实现搜索框从按钮位置展开/收缩（F7），消除整栏横滑的生硬感 =====
            AnimatedContent(
                targetState = if (selectionMode) TopBarState.MULTI_SELECT else TopBarState.CONTENT,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(200)) +
                        slideInHorizontally(animationSpec = tween(200)) { it / 8 })
                        .togetherWith(fadeOut(animationSpec = tween(150)))
                },
                label = "HistoryTopBar"
            ) { state ->
                when (state) {
                    // ===== 多选模式顶栏（H2） =====
                    TopBarState.MULTI_SELECT -> {
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

                    // ===== 普通/搜索合并态顶栏（F3/F4/F5/F7/F8 修正 2026-08-16 13:56） =====
                    TopBarState.CONTENT -> {
                        TopAppBar(
                            title = {
                                // F7：标题行与搜索框用 AnimatedVisibility 互斥切换——
                                // 搜索框从标题右侧按钮位置（Alignment.End）向左展开成整行，
                                // 关闭时反向收缩回按钮位置，实现"从按钮扩大成搜索框"的过渡
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    // 搜索框（H3）：进入时从右端展开 + 淡入，退出时向右收缩 + 淡出
                                    AnimatedVisibility(
                                        visible = isSearching,
                                        enter = expandHorizontally(
                                            expandFrom = Alignment.End,
                                            animationSpec = tween(220)
                                        ) + fadeIn(animationSpec = tween(220)),
                                        exit = shrinkHorizontally(
                                            shrinkTowards = Alignment.End,
                                            animationSpec = tween(180)
                                        ) + fadeOut(animationSpec = tween(180))
                                    ) {
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
                                    }
                                    // 标题行：退出时向左收缩消失，与搜索框右端展开形成衔接
                                    AnimatedVisibility(
                                        visible = !isSearching,
                                        enter = fadeIn(animationSpec = tween(220)),
                                        exit = shrinkHorizontally(
                                            shrinkTowards = Alignment.Start,
                                            animationSpec = tween(180)
                                        ) + fadeOut(animationSpec = tween(180))
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(stringResource(R.string.history_title))
                                            if (notifications.isNotEmpty()) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                // F4：搜索入口紧邻"历史记录"标题
                                                // F8：图标恢复 Material 标准 24dp，与标题文字视觉等大
                                                IconButton(
                                                    onClick = { isSearching = true },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Search,
                                                        contentDescription = stringResource(R.string.history_search)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            actions = {
                                if (isSearching) {
                                    // 搜索态：关闭搜索并清空关键词
                                    IconButton(onClick = {
                                        isSearching = false
                                        viewModel.setSearchQuery("")
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                                    }
                                } else if (notifications.isNotEmpty()) {
                                    // F3：布局切换按钮图标跟随当前模式（单列态显示 ViewAgenda，两列态显示 ViewWeek）
                                    IconButton(onClick = {
                                        viewModel.setHistoryLayoutMode(
                                            if (isTwoColumn) "single" else "two_column"
                                        )
                                    }) {
                                        Icon(
                                            imageVector = if (isTwoColumn) Icons.Default.ViewWeek else Icons.Default.ViewAgenda,
                                            contentDescription = stringResource(
                                                if (isTwoColumn) R.string.history_layout_single
                                                else R.string.history_layout_two_column
                                            )
                                        )
                                    }
                                    // F5：清空全部按钮已移除，清空场景走"长按→全选→删除选中"
                                }
                            }
                        )
                    }
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
                                Icons.AutoMirrored.Filled.Send,
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

            // ===== 两列网格（F1 修正 2026-08-16 13:30） =====
            // 原实现用 item{Spacer} 做上下留白，Spacer 会独占第一列首格，
            // 导致左上大片空白、首条记录排右上、整体非Z字形。
            // 改用 contentPadding 后网格恢复 1 2 / 3 4 行优先 Z 字形排布。
            isTwoColumn -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                }
            }

            // ===== 单列列表（F1：改 contentPadding 保持与网格一致的上下留白） =====
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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

    // F5（2026-08-16 13:30）：清空全部确认框已随顶栏按钮一并移除，
    // 清空场景走"长按 → 全选 → 删除选中"（deleteAllNotifications 保留为数据层 API）。
}

/**
 * 顶栏双态（F2 新增 2026-08-16，F7 精简 2026-08-16）：
 * CONTENT = 普通/搜索合并态（内部 AnimatedVisibility 展开搜索框），
 * MULTI_SELECT = 多选态，供 AnimatedContent 整体切换
 */
private enum class TopBarState { CONTENT, MULTI_SELECT }

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
                                Icons.AutoMirrored.Filled.Send,
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
                                Icons.AutoMirrored.Filled.Send,
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
