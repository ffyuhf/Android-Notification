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
            // 权限获取后启动前台服务
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

        // 初始化 ViewModel
        viewModel = ViewModelProvider(this)[NotifyViewModel::class.java]

        // 检查并请求权限
        checkAndRequestPermissions()

        // 设置 Compose 内容
        setContent {
            val darkMode by viewModel.darkMode.collectAsState()
            NotifyAppTheme(darkMode = darkMode) {
                MainContent(viewModel = viewModel)
            }
        }
    }

    /**
     * 检查并请求必要权限
     *
     * 必需权限：通知权限
     * 可选权限：精确闹钟权限（Android 12+）
     */
    private fun checkAndRequestPermissions() {
        // 1. 通知权限（Android 13+ 必需）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // 已有权限，启动服务
                NotifyForegroundService.start(this)
            }
        } else {
            // Android 13以下直接启动服务
            NotifyForegroundService.start(this)
        }

        // 2. 精确闹钟权限（Android 12+ 可选）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(android.app.AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                // 引导用户到设置页面开启精确闹钟权限
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
 * 底部导航 + 页面切换
 */
@Composable
private fun MainContent(viewModel: NotifyViewModel) {
    var selectedItem = remember { androidx.compose.runtime.mutableStateOf(0) }

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
                        selected = selectedItem.value == index,
                        onClick = { selectedItem.value = index }
                    )
                }
            }
        }
    ) { paddingValues ->
        when (selectedItem.value) {
            0 -> HomeScreen(viewModel).let {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.padding(paddingValues)
                ) { it }
            }
            1 -> androidx.compose.foundation.layout.Box(
                modifier = Modifier.padding(paddingValues)
            ) { HistoryScreen(viewModel) }
            2 -> androidx.compose.foundation.layout.Box(
                modifier = Modifier.padding(paddingValues)
            ) { SettingsScreen(viewModel) }
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

/**
 * remember 辅助函数
 */
@Composable
private fun remember(initial: () -> androidx.compose.runtime.MutableState<Int>): androidx.compose.runtime.MutableState<Int> {
    return androidx.compose.runtime.remember { initial() }
}
