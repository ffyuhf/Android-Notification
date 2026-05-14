package com.android.notify

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
 * 2. 动态权限请求（通知、闹钟、开机启动）
 * 3. 启动前台保活服务
 *
 * 创建日期：2026-05-14
 * 作者：Cline
 */
class MainActivity : ComponentActivity() {

    private lateinit var viewModel: NotifyViewModel

    /** 通知权限请求 */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            NotifyForegroundService.start(this)
        } else {
            Toast.makeText(this, R.string.permission_notification_denied, Toast.LENGTH_LONG).show()
        }
    }

    /** 精确闹钟权限请求（Android 12+） */
    private val exactAlarmPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 精确闹钟权限结果回调
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[NotifyViewModel::class.java]
        checkAndRequestPermissions()

        setContent {
            val darkMode by viewModel.darkMode.collectAsState()
            NotifyAppTheme(darkMode = darkMode) {
                MainContent(viewModel = viewModel)
            }
        }
    }

    /**
     * 检查并请求必要权限
     */
    private fun checkAndRequestPermissions() {
        // 通知权限（Android 13+ 必需）
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

        // 精确闹钟权限（Android 12+ 可选）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(android.app.AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(
                    android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                ).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                exactAlarmPermissionLauncher.launch(intent)
            }
        }
    }
}

/**
 * 主内容 Composable
 *
 * 底部导航 + 页面切换。
 * 修复：使用标准 remember + mutableIntStateOf，移除自定义 remember 遮蔽。
 * 修复：页面渲染使用正确的 Compose 组合方式，避免 .let 导致的闪退。
 */
@Composable
private fun MainContent(viewModel: NotifyViewModel) {
    // 使用 mutableIntStateOf 避免不必要的重组
    var selectedItem by remember { mutableIntStateOf(0) }

    val items = listOf(
        NavigationItem(stringResource(R.string.tab_home), Icons.Default.Home),
        NavigationItem(stringResource(R.string.tab_history), Icons.Default.History),
        NavigationItem(stringResource(R.string.tab_settings), Icons.Default.Settings)
    )

    Scaffold(
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
        // 正确的 Compose 页面渲染，避免 .let 导致的组合问题
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
