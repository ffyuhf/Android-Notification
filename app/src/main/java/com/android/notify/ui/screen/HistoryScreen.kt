package com.android.notify.ui.screen

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.notify.EditNotificationActivity
import com.android.notify.R
import com.android.notify.data.db.entity.NotificationEntity
import com.android.notify.ui.component.AdaptiveImage
import com.android.notify.util.NotificationHelper
import com.android.notify.viewmodel.NotifyViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 历史记录页面
 *
 * 功能：
 * 1. 显示所有通知历史记录列表（单列 / 两列瀑布流可切换，H1）
 * 2. 每条记录可重新发送或删除（删除需二次确认，H2）
 * 3. 长按进入多选模式：全选 / 反选、批量删除 / 批量发送（H2）
 * 4. 顶栏搜索实时过滤标题与内容（H3）
 *
 * MD3 重绘（2026-08-18 15:57 | 界面MD3全面重绘）：
 * - P4 多选批量操作由页内底部操作栏改为顶栏 actions 化（Google Photos 式）：
 *   批量发送/删除为顶栏图标按钮，全选/反选收入溢出菜单，
 *   彻底移除内层 bottomBar，消除与全局 NavigationBar 的双层底栏叠置
 * - P6 卡片重绘：容器色 surfaceContainerLow（P8）、图片全宽置顶、
 *   状态由 ●/○/⏰ 文本符号改为 AssistChip（已固定 / 已定时+时间，普通态不占位）
 * - P9 空状态增加图标引导
 * - P5 保留既有搜索展开/收起过渡（F7/F9 成果），仅统一顶栏配色
 *
 * 动画历史块权限修复（2026-08-18 16:46）：
 * - A2 顶栏多选切换改淡入+垂直轻移对称补间（原横滑进出节奏不一致）
 * - A3 搜索框恒定宽度+位移淡入（原 expandHorizontally 展开挤压文字）
 * - A4 列表项 Modifier.animateItem 增删/置顶位移动画（依赖 items key）
 * - B1-B3 卡片元数据区重构：状态 AssistChip 改行内图标+文本指示
 *   （HistoryStatusIndicator），单列一行同水平面 36dp 按钮，两列两行紧凑
 *   + formatShortDateTime 短时间（修复窄卡时间显示不全/指示换行错位）
 *
 * 历史操作菜单改造（2026-08-18 20:46）：
 * - 卡内右下角编辑/发送/删除三按钮移除，改由短按卡片弹出 ModalBottomSheet
 *   底部二级菜单承载（MD3 组件契约）；多选短按切换选中、长按进多选不变；
 *   删除仍走 pendingSingleDelete 二次确认（H2 契约保持）
 *
 * 契约保持：两列瀑布流 LazyVerticalStaggeredGrid（各列独立测量）；
 * 图片缩略图经 AdaptiveImage 完整显示（异步解码 + 限高 + Fit）。
 *
 * 创建日期：2026-05-14 | 作者：Cline
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(viewModel: NotifyViewModel) {
    val context = LocalContext.current

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

    // ===== 底部操作菜单状态（改造 2026-08-18 20:46）=====
    /** 菜单目标记录：非空即弹出 ModalBottomSheet */
    var actionSheetEntity by remember { mutableStateOf<NotificationEntity?>(null) }
    /** skipPartiallyExpanded=true 直达全展开，避免三项菜单出现半展开中间态 */
    val actionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetScope = rememberCoroutineScope()

    /** 收起底部菜单：先走 hide 收起动画，动画结束后清空目标状态（避免瞬跳消失） */
    val dismissActionSheet: () -> Unit = {
        sheetScope.launch { actionSheetState.hide() }
            .invokeOnCompletion { actionSheetEntity = null }
    }

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
            // ===== 双态顶栏：多选态与普通/搜索态整体切换（淡入+轻微垂直位移）；
            // 普通↔搜索 态合并为单 TopAppBar，搜索框从按钮位置平移过渡（F7/F9 语义保持）。
            // 修正（2026-08-18 16:46 | 动画历史块权限修复 A2）：原横滑进出节奏
            // 不一致（进入横滑 200ms / 退出仅淡出 150ms）观感突兀，
            // 改对称补间：新栏自下方轻移淡入、旧栏向上轻移淡出（各 200/180ms）=====
            AnimatedContent(
                targetState = if (selectionMode) TopBarState.MULTI_SELECT else TopBarState.CONTENT,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(200)) +
                        slideInVertically(animationSpec = tween(200)) { it / 10 })
                        .togetherWith(
                            fadeOut(animationSpec = tween(180)) +
                                slideOutVertically(animationSpec = tween(180)) { -it / 10 }
                        )
                },
                label = "HistoryTopBar"
            ) { state ->
                when (state) {
                    // ===== 多选模式顶栏（MD3 重绘 P4：批量操作顶栏化，替代底部批量栏）=====
                    TopBarState.MULTI_SELECT -> {
                        var menuExpanded by remember { mutableStateOf(false) }
                        TopAppBar(
                            navigationIcon = {
                                IconButton(onClick = exitSelection) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.history_exit_multi_select)
                                    )
                                }
                            },
                            title = {
                                Text(stringResource(R.string.history_selected_count, selectedIds.size))
                            },
                            actions = {
                                // 批量发送：逐条按现有重发链路执行（D3）
                                IconButton(
                                    onClick = {
                                        val entities = notifications.filter { selectedIds[it.id] == true }
                                        if (entities.isNotEmpty()) {
                                            viewModel.resendNotifications(entities)
                                        }
                                        exitSelection()
                                    },
                                    enabled = selectedIds.isNotEmpty()
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = stringResource(R.string.history_send_selected),
                                        tint = if (selectedIds.isNotEmpty()) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                // 批量删除：需二次确认（H2）
                                IconButton(
                                    onClick = { showDeleteSelectedDialog = true },
                                    enabled = selectedIds.isNotEmpty()
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.history_delete_selected),
                                        tint = if (selectedIds.isNotEmpty()) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                // 全选/反选：低频操作收入溢出菜单
                                Box {
                                    IconButton(onClick = { menuExpanded = true }) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = null
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.history_select_all)) },
                                            onClick = {
                                                selectAllVisible()
                                                menuExpanded = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.history_invert_selection)) },
                                            onClick = {
                                                invertVisibleSelection()
                                                menuExpanded = false
                                            }
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        )
                    }

                    // ===== 普通/搜索合并态顶栏（搜索展开机制保留 F7/F9）=====
                    TopBarState.CONTENT -> {
                        TopAppBar(
                            title = {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    // 搜索框（H3）：宽度恒定（fillMaxWidth 不参与宽度动画），
                                    // 自标题位（Start 侧）平移 + 淡入，关闭时反向收回。
                                    // 修正（2026-08-18 16:46 | A3）：原 expandHorizontally
                                    // 宽度展开过程持续挤压输入框内容（placeholder/已输入
                                    // 文字水平压缩变形），改恒定宽度 + 位移淡入消除变形
                                    AnimatedVisibility(
                                        visible = isSearching,
                                        enter = fadeIn(animationSpec = tween(220)) +
                                            slideInHorizontally(animationSpec = tween(220)) { -it / 8 },
                                        exit = fadeOut(animationSpec = tween(180)) +
                                            slideOutHorizontally(animationSpec = tween(180)) { -it / 8 }
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
                                    // 标题行：退出时仅淡出，避免整栏横滑观感（F9）
                                    AnimatedVisibility(
                                        visible = !isSearching,
                                        enter = fadeIn(animationSpec = tween(220)),
                                        exit = fadeOut(animationSpec = tween(180))
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(stringResource(R.string.history_title))
                                            if (notifications.isNotEmpty()) {
                                                Spacer(modifier = Modifier.width(4.dp))
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
                                    // 布局切换：图标跟随当前模式（单列态 ViewAgenda，两列态 ViewWeek）
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
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                }
            }
        }
        // MD3 重绘 P4：多选批量操作已顶栏化，页内不再有 bottomBar，
        // 全局 NavigationBar 始终为唯一底栏（消除双层底栏叠置）
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

            // ===== 两列瀑布流（契约保持：StaggeredGrid 各列独立测量，无图卡片紧凑合排）=====
            isTwoColumn -> {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 12.dp),
                    verticalItemSpacing = 8.dp,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    staggeredItems(notifications, key = { it.id }) { notification ->
                        CompactNotificationCard(
                            notification = notification,
                            selectionMode = selectionMode,
                            isSelected = selectedIds[notification.id] == true,
                            // 短按弹底部操作菜单（改造 2026-08-18 20:46）；
                            // 多选模式保持切换选中不变
                            onItemClick = {
                                if (selectionMode) {
                                    toggleSelection(notification)
                                } else {
                                    actionSheetEntity = notification
                                }
                            },
                            onItemLongClick = { if (!selectionMode) enterSelection(notification) },
                            // 列表项增删/置顶位移动画（2026-08-18 16:46 | A4）：
                            // 依赖 items key 生效，消除记录增删时列表瞬间跳变
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }

            // ===== 单列列表 =====
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
                            // 短按弹底部操作菜单（改造 2026-08-18 20:46）；
                            // 多选模式保持切换选中不变
                            onItemClick = {
                                if (selectionMode) {
                                    toggleSelection(notification)
                                } else {
                                    actionSheetEntity = notification
                                }
                            },
                            onItemLongClick = { if (!selectionMode) enterSelection(notification) },
                            // 列表项增删/置顶位移动画（2026-08-18 16:46 | A4）：
                            // 依赖 items key 生效，消除记录增删时列表瞬间跳变
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }

    // ===== 单条删除二级确认（H2）=====
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

    // ===== 批量删除二级确认（H2）=====
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

    // ===== 底部操作菜单（改造 2026-08-18 20:46）：短按卡片弹出，
    // 替代原卡内右下角三按钮；编辑/发送收起菜单后直接执行，
    // 删除转 pendingSingleDelete 二次确认（H2 契约保持）=====
    actionSheetEntity?.let { entity ->
        ModalBottomSheet(
            onDismissRequest = { actionSheetEntity = null },
            sheetState = actionSheetState
        ) {
            // 上下文标题行：定位当前操作对象（标题为空回退内容或图片占位文案）
            Text(
                text = entity.title?.ifBlank { null }
                    ?: entity.content.ifBlank { stringResource(R.string.image_only_notification) },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            )
            // 编辑（应用内编辑页：改标题+图片）
            HistorySheetAction(
                icon = Icons.Default.Edit,
                label = stringResource(R.string.action_edit),
                tint = MaterialTheme.colorScheme.onSurface
            ) {
                dismissActionSheet()
                launchEditPage(context, entity.id)
            }
            // 重新发送（复用单条重发链路，响铃按设置决定）
            HistorySheetAction(
                icon = Icons.AutoMirrored.Filled.Send,
                label = stringResource(R.string.btn_resend),
                tint = MaterialTheme.colorScheme.primary
            ) {
                dismissActionSheet()
                viewModel.resendNotification(entity)
            }
            // 删除（保留 H2 二次确认）
            HistorySheetAction(
                icon = Icons.Default.Delete,
                label = stringResource(R.string.btn_delete),
                tint = MaterialTheme.colorScheme.error
            ) {
                dismissActionSheet()
                pendingSingleDelete = entity
            }
            // 底部安全留白（避开手势导航区）
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 应用内编辑页入口：负责改标题+图片（新增 2026-08-16，MD3 重绘抽取为函数消除两处重复）
 *
 * @param context 上下文
 * @param notificationId 待编辑的通知记录 ID
 */
private fun launchEditPage(context: android.content.Context, notificationId: Int) {
    context.startActivity(
        Intent(context, EditNotificationActivity::class.java).apply {
            action = "EDIT_$notificationId"
            putExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, notificationId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    )
}

/**
 * 底部操作菜单项（新增 2026-08-18 20:46 | 历史操作菜单改造）
 *
 * MD3 菜单列表项样式：leading 图标 + 文本整行点击；
 * 语义色由调用方传入（编辑 onSurface / 发送 primary / 删除 error）。
 *
 * @param icon 行首图标
 * @param label 操作文案
 * @param tint 图标与文本颜色
 * @param onClick 点击回调（调用方负责先收起菜单再执行动作）
 */
@Composable
private fun HistorySheetAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

/**
 * 顶栏双态：CONTENT = 普通/搜索合并态，MULTI_SELECT = 多选态
 */
private enum class TopBarState { CONTENT, MULTI_SELECT }

/**
 * 历史卡片显示状态（MD3 重绘 P6：替代 ●/○/⏰ 文本符号的结构化状态）
 */
private sealed interface HistoryItemStatus {
    /** 已固定：通知处于固定展示中 */
    data object Pinned : HistoryItemStatus
    /** 已定时未触发：scheduledAt 为计划触发时间 */
    data class Scheduled(val triggerAtMillis: Long) : HistoryItemStatus
    /** 普通：已发送的历史记录，不渲染状态 Chip（减少视觉噪音） */
    data object Normal : HistoryItemStatus
}

/**
 * 解析记录显示状态（原 formatStatusText 的结构化改造，判定逻辑不变）
 *
 * @param notification 通知实体
 * @return Pinned / Scheduled / Normal
 */
private fun resolveItemStatus(notification: NotificationEntity): HistoryItemStatus = when {
    !notification.isActive && notification.scheduledAt != null ->
        HistoryItemStatus.Scheduled(notification.scheduledAt)
    notification.isPinned -> HistoryItemStatus.Pinned
    else -> HistoryItemStatus.Normal
}

/**
 * 历史卡片状态轻量指示（重构 2026-08-18 16:44 | 动画历史块权限修复 B3）
 *
 * 原 AssistChip 实现（MD3 重绘 P6）带边框/最小高度/固定内边距，在窄卡片中
 * 不可压缩，导致换行错位、与操作按钮不在同一水平面、占用多余高度。
 * 现改为行内「图标 + 文本」组合：恒单行（超宽省略）、高度由文字行高决定，
 * 可与时间、操作按钮稳定同行排列；配色语义保持 P6 决策
 * （已固定 primary 色 + 图钉；已定时 onSurfaceVariant + 时钟 + 触发时间；
 * 普通态不渲染，减少视觉噪音）。
 *
 * @param status 记录显示状态
 */
@Composable
private fun HistoryStatusIndicator(status: HistoryItemStatus) {
    when (status) {
        HistoryItemStatus.Pinned -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.PushPin,
                    contentDescription = stringResource(R.string.status_pinned),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = stringResource(R.string.status_pinned),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        is HistoryItemStatus.Scheduled -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = stringResource(R.string.status_scheduled),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = formatShortDateTime(status.triggerAtMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        HistoryItemStatus.Normal -> Unit
    }
}

/**
 * 空状态提示（MD3 重绘 P9：增加图标引导）
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
        Icon(
            imageVector = Icons.Default.Notifications,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 单条历史记录卡片（单列模式，MD3 重绘 P6/P8）
 *
 * 结构：图片全宽置顶 → 文字区（标题/内容）→ 底部元数据行（时间 + 状态指示）。
 * 多选模式：整卡点击切换选中并显示勾选框。
 * 改造（2026-08-18 20:46）：卡内操作按钮移除，编辑/发送/删除统一由
 * 短按卡片弹出的底部二级菜单承载（见 HistoryScreen 的 ModalBottomSheet）。
 *
 * @param notification 通知实体
 * @param selectionMode 是否处于多选模式
 * @param isSelected 当前记录是否被选中
 * @param onItemClick 点击回调（多选模式切换选中；普通模式弹出操作菜单）
 * @param onItemLongClick 长按回调（进入多选并选中）
 * @param modifier 外部布局修饰（列表调用方传入 animateItem 实现增删位移动画）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotificationHistoryItem(
    notification: NotificationEntity,
    selectionMode: Boolean,
    isSelected: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onItemLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 图片缩略图：全宽置顶（AdaptiveImage 契约：按图比例自适应完整显示）
            // 调整（2026-08-18 20:00 | 图片清晰度修复）：移除硬编码解码目标，
            // 组件自动按显示区物理像素 1:1 解码（视觉原图）
            if (!notification.imagePath.isNullOrBlank()) {
                AdaptiveImage(
                    path = notification.imagePath,
                    maxHeight = 200.dp,
                    cornerRadius = 12.dp,
                    contentDescription = stringResource(R.string.action_add_image)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // 多选勾选框（仅多选模式显示）
                if (selectionMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isSelected, onCheckedChange = { onItemClick() })
                    }
                }

                // 标题（可选，1行省略）
                if (!notification.title.isNullOrBlank()) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // 内容（3行省略；纯图通知 content 为空串，显示「[图片]」占位）
                Text(
                    text = notification.content.ifBlank {
                        stringResource(R.string.image_only_notification)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 底部元数据行（改造 2026-08-18 20:46）：时间 + 状态指示；
                // 操作按钮已移至短按弹出的底部菜单，卡内不再渲染按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatHistoryTime(notification.createdAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    HistoryStatusIndicator(status = resolveItemStatus(notification))
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * 双列紧凑历史记录卡片（两列瀑布流模式，MD3 重绘 P6/P8）
 *
 * 信息精简排布：图片全宽置顶 + 标题1行 + 内容2行 + 时间/状态指示行。
 * 多选模式下整卡点击切换选中并显示勾选框。
 * 改造（2026-08-18 20:46）：卡内操作按钮行移除，编辑/发送/删除统一由
 * 短按卡片弹出的底部二级菜单承载（见 HistoryScreen 的 ModalBottomSheet）。
 *
 * @param notification 通知实体
 * @param selectionMode 是否处于多选模式
 * @param isSelected 当前记录是否被选中
 * @param onItemClick 点击回调（多选模式切换选中；普通模式弹出操作菜单）
 * @param onItemLongClick 长按回调（进入多选并选中）
 * @param modifier 外部布局修饰（列表调用方传入 animateItem 实现增删位移动画）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactNotificationCard(
    notification: NotificationEntity,
    selectionMode: Boolean,
    isSelected: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onItemLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 图片缩略图：全宽置顶（AdaptiveImage 契约保持）
            // 调整（2026-08-18 20:00 | 图片清晰度修复）：移除硬编码解码目标，
            // 组件自动按显示区物理像素 1:1 解码（视觉原图）
            if (!notification.imagePath.isNullOrBlank()) {
                AdaptiveImage(
                    path = notification.imagePath,
                    maxHeight = 160.dp,
                    cornerRadius = 12.dp,
                    contentDescription = stringResource(R.string.action_add_image)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                // 多选勾选框（仅多选模式显示）
                if (selectionMode) {
                    Checkbox(checked = isSelected, onCheckedChange = { onItemClick() })
                }

                // 标题（可选，1行省略）
                if (!notification.title.isNullOrBlank()) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // 内容摘要（2行省略；纯图通知显示「[图片]」占位）
                Text(
                    text = notification.content.ifBlank {
                        stringResource(R.string.image_only_notification)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 底部元数据行（改造 2026-08-18 20:46）：时间短格式（去年份保证窄卡
                // 完整显示）+ 状态指示，恒单行；原行2 操作按钮行已移至底部菜单
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatShortDateTime(notification.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    HistoryStatusIndicator(status = resolveItemStatus(notification))
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * 格式化历史记录创建时间显示
 *
 * @param timeMillis 创建时间戳
 * @return yyyy-MM-dd HH:mm 格式文本
 */
private fun formatHistoryTime(timeMillis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(timeMillis)
}

/**
 * 格式化短日期时间（重构 2026-08-18 16:44 | B2/B3）
 *
 * 原 formatScheduledChipTime 仅服务定时状态 Chip 文案；Chip 去 AssistChip 化后，
 * 该短格式同时用于两列窄卡片的创建时间显示（去年份适配窄卡宽度），
 * 按实际用途重命名。
 *
 * @param timeMillis 时间戳
 * @return MM-dd HH:mm 格式文本
 */
private fun formatShortDateTime(timeMillis: Long): String {
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(timeMillis)
}
