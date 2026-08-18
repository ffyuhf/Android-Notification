package com.android.notify

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.unit.dp
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
 * - 页面切换过渡动画（2026-08-18 16:42 | 动画历史块权限修复 A1）：
 *   when 直切改 AnimatedContent 淡入+轻微上滑，消除切 Tab 生硬直切
 * - 通知权限回调三分支（2026-08-18 16:42 | 同上 C1）：对齐 Android 权限技能模板 4.8，
 *   拒绝可再询问→展示用途说明后可重试；永久拒绝→提示并引导跳通知设置页
 *
 * 创建日期：2026-05-14 | 作者：Cline
 */
class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: NotifyViewModel

    /**
     * 通知权限请求（dangerous 权限，运行时弹窗）
     *
     * 回调三分支（2026-08-18 16:42 | 对齐 Android 权限技能模板 4.8）：
     * ① 授权 → 启动前台保活服务；
     * ② 拒绝但可再询问（rationale 为 true）→ 展示权限用途说明，用户可重新申请；
     * ③ 拒绝且永久拒绝（不再询问，系统不再弹窗）→ Toast 提示并引导跳应用通知设置页。
     */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        when {
            // 分支①：授权成功
            granted -> NotifyForegroundService.start(this)
            // 分支②：拒绝但允许再次询问 → 先说明用途再由用户决定是否重试
            ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) -> showNotificationPermissionRationale()
            // 分支③：永久拒绝 → 系统不再弹窗，只能去系统设置手动开启
            else -> {
                Toast.makeText(
                    this,
                    R.string.permission_notification_permanently_denied,
                    Toast.LENGTH_LONG
                ).show()
                openNotificationSettings()
            }
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

    /**
     * 展示通知权限用途说明（模板 4.8 分支②：拒绝但可再询问场景）
     *
     * 用户此前拒绝过一次，系统建议先解释权限用途再重新申请；
     * 「重新申请」二次拉起系统权限弹窗，「取消」保留现状（下次启动仍会检查）。
     */
    private fun showNotificationPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_notification_rationale_title)
            .setMessage(R.string.permission_notification_rationale_desc)
            .setPositiveButton(R.string.permission_request_again) { _, _ ->
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * 跳转系统应用通知设置页（模板 4.8 分支③：永久拒绝引导）
     *
     * 与设置页权限入口（SettingsScreen U1）跳转目标一致，直达本应用通知开关。
     */
    private fun openNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        )
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
        // MD3 重绘（2026-08-18）：三页（Home/History/Settings）均已设 TopAppBar，
        // 状态栏留白统一由各页顶栏自带窗口 inset 处理；消除嵌套 Scaffold
        // 状态栏 inset 双算导致的顶部空白
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
            // 页面切换过渡（2026-08-18 20:04 | 图片清晰度图标与转场动画修复）：
            // 改为方向感知水平 SharedAxis 转场（Material Motion 底部导航规范）——
            // Tab 索引增大时新页自右滑入（前进），减小时自左滑入（返回），
            // 旧页向对侧滑出，位移幅度 1/4 屏宽保持克制；
            // 替换原垂直窜动方案（旧页上移/新页下移，方向感与底部导航不符）；
            // rememberSaveable 选中态与各页内部输入状态经 SaveableStateRegistry 保持不变
            AnimatedContent(
                targetState = selectedItem,
                transitionSpec = {
                    // 方向感知：目标 Tab 索引大于当前为前进（右滑），否则返回（左滑）
                    val slideDirection = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) { slideDirection * it / 4 } +
                        fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)))
                        .togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(250, easing = FastOutSlowInEasing)
                            ) { -slideDirection * it / 4 } +
                                fadeOut(animationSpec = tween(250, easing = FastOutSlowInEasing))
                        )
                },
                label = "PageSwitch"
            ) { page ->
                when (page) {
                    0 -> HomeScreen(viewModel)
                    1 -> HistoryScreen(viewModel)
                    2 -> SettingsScreen(viewModel)
                }
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
