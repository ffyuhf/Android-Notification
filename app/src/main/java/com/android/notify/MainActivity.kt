package com.android.notify

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.android.notify.service.NotifyForegroundService
import com.android.notify.ui.screen.HistoryScreen
import com.android.notify.ui.screen.HomeScreen
import com.android.notify.ui.screen.SettingsScreen
import com.android.notify.ui.theme.NotifyAppTheme
import com.android.notify.viewmodel.NotifyViewModel

/**
 * 主 Activity
 *
 * 职责：
 * 1. Compose 入口，承载底部导航和页面切换
 * 2. 通知权限动态请求（Android 13+）
 * 3. 启动前台保活服务
 *
 * 优化（2026-08-16）：
 * - B4 改继承 AppCompatActivity，支持 per-app 语言切换（AppCompatDelegate）
 * - B5 消费 viewModel.message 显示操作结果 Toast（原状态无消费者永不显示）
 * - U1 移除启动时精确闹钟权限强跳（改为定时发送时按需引导，见 HomeScreen）
 * - U3 底部导航选中状态改 rememberSaveable，切页重建后保留
 * - F6 外层 Scaffold contentWindowInsets 清零，消除嵌套 Scaffold 状态栏 inset 双算
 *
 * 创建日期：2026-05-14 | 作者：Cline
 */
class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: NotifyViewModel

    /** 通知权限请求（dangerous 权限，运行时弹窗） */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            NotifyForegroundService.start(this)
        } else {
            Toast.makeText(this, R.string.permission_notification_denied, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[NotifyViewModel::class.java]
        checkAndRequestNotificationPermission()

        setContent {
            val darkMode by viewModel.darkMode.collectAsState()
            NotifyAppTheme(darkMode = darkMode) {
                MainContent(viewModel = viewModel)
            }
        }
    }

    /**
     * 检查并请求通知权限（Android 13+）
     *
     * 优化（2026-08-16 | U1）：精确闹钟权限引导从启动流程移除。
     * SCHEDULE_EXACT_ALARM 属特殊访问权限（跳设置页授权），
     * 非运行时弹窗权限，启动即强跳设置页打扰用户；
     * 现改为定时发送确认时按需引导（HomeScreen）并在设置页提供手动入口。
     */
    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                NotifyForegroundService.start(this)
            }
        } else {
            NotifyForegroundService.start(this)
        }
    }
}

/**
 * 主内容 Composable
 *
 * 底部导航 + 页面切换。
 * 优化（2026-08-16）：
 * - B5 收集 message 状态显示操作结果 Toast 后清除
 * - U3 selectedItem 使用 rememberSaveable 保留选中页
 */
@Composable
private fun MainContent(viewModel: NotifyViewModel) {
    // rememberSaveable 避免切页/重建后丢失选中Tab（U3）
    var selectedItem by rememberSaveable { mutableIntStateOf(0) }

    // 消费操作结果消息显示Toast（B5：原 message 状态无消费者，Toast 永不显示）
    val context = LocalContext.current
    val message by viewModel.message.collectAsState()
    LaunchedEffect(message) {
        message?.let { resId ->
            Toast.makeText(context, resId, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    val items = listOf(
        NavigationItem(stringResource(R.string.tab_home), Icons.Default.Home),
        NavigationItem(stringResource(R.string.tab_history), Icons.Default.History),
        NavigationItem(stringResource(R.string.tab_settings), Icons.Default.Settings)
    )

    Scaffold(
        // F6（2026-08-16 13:56）：外层导航壳不再叠加系统栏 inset。
        // 状态栏留白交由子页面自管——History/Settings 的 TopAppBar 自带窗口 inset，
        // Home 由 statusBarsPadding 补偿；消除嵌套 Scaffold 状态栏 inset 双算导致的顶部空白
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = selectedItem == index,
                        onClick = { selectedItem = index }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedItem) {
                0 -> HomeScreen(viewModel)
                1 -> HistoryScreen(viewModel)
                2 -> SettingsScreen(viewModel)
            }
        }
    }
}

/**
 * 导航项数据类
 */
private data class NavigationItem(
    val label: String,
    val icon: ImageVector
)
