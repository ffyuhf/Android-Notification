package io.github.ffyuhf.notify

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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
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
import io.github.ffyuhf.notify.service.NotifyForegroundService
import io.github.ffyuhf.notify.ui.screen.AboutScreen
import io.github.ffyuhf.notify.ui.screen.HistoryScreen
import io.github.ffyuhf.notify.ui.screen.HomeScreen
import io.github.ffyuhf.notify.ui.screen.SettingsScreen
import io.github.ffyuhf.notify.ui.theme.NotifyAppTheme
import io.github.ffyuhf.notify.viewmodel.NotifyViewModel

/**
 * 主 Activity
 *
 * 职责：
 * 1. Compose 入口，承载底部导航和页面切换
 * 2. 通知权限动态请求（Android 13+，回调三分支：授权/可再询问/永久拒绝）
 * 3. 启动前台保活服务
 * 4. 继承 AppCompatActivity 支持 per-app 语言切换（AppCompatDelegate）
 */
class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: NotifyViewModel

    /**
     * 通知权限请求（dangerous 权限，运行时弹窗）
     *
     * 回调三分支：
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
     * 精确闹钟权限引导不在启动流程：SCHEDULE_EXACT_ALARM 属特殊访问权限
     * （跳设置页授权），启动即强跳打扰用户；改为定时发送确认时按需引导
     * （HomeScreen）并在设置页提供手动入口。
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
     * 展示通知权限用途说明（分支②：拒绝但可再询问场景）
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
     * 跳转系统应用通知设置页（分支③：永久拒绝引导）
     *
     * 与设置页权限入口跳转目标一致，直达本应用通知开关。
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
 * 底部导航 + 页面切换；收集 message 状态显示操作结果 Toast 后清除。
 */
@Composable
private fun MainContent(viewModel: NotifyViewModel) {
    // rememberSaveable 避免切页/重建后丢失选中Tab
    var selectedItem by rememberSaveable { mutableIntStateOf(0) }

    // 消费操作结果消息显示 Toast 后清除
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
        // 外层导航壳不叠加系统栏 inset：三页均已设 TopAppBar，状态栏留白
        // 统一由各页顶栏自带窗口 inset 处理；消除嵌套 Scaffold 状态栏
        // inset 双算导致的顶部空白
        contentWindowInsets = WindowInsets(0.dp),
        // 关于页（selectedItem == 3）为独立二级页面：底栏收起/恢复带垂直
        // 滑动+淡入淡出过渡（MD3 二级页惯例，全局 NavigationBar 仍是唯一底栏）
        bottomBar = {
            AnimatedVisibility(
                visible = selectedItem != 3,
                enter = fadeIn(animationSpec = tween(200)) +
                    slideInVertically(animationSpec = tween(200)) { it },
                exit = fadeOut(animationSpec = tween(180)) +
                    slideOutVertically(animationSpec = tween(180)) { it },
                label = "BottomBarVisibility"
            ) {
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
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            // 页面切换过渡：方向感知水平 SharedAxis 转场（Material Motion 底部导航规范）——
            // Tab 索引增大时新页自右滑入（前进），减小时自左滑入（返回），
            // 旧页向对侧滑出，位移幅度 1/4 屏宽保持克制；
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
                    // 关于页：独立二级页面，顶栏返回箭头/返回手势经 onBack 回设置页
                    // （2→3 右滑前进、3→2 左滑返回，复用方向感知转场语义）
                    2 -> SettingsScreen(viewModel, onOpenAbout = { selectedItem = 3 })
                    3 -> AboutScreen(onBack = { selectedItem = 2 })
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
