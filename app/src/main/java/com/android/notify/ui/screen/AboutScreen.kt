package com.android.notify.ui.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Copyright
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.notify.BuildConfig
import com.android.notify.R

/** 项目仓库地址 */
private const val PROJECT_URL = "https://github.com/ffyuhf/Android-Notification"

/** 问题反馈页地址（GitHub Issues） */
private const val ISSUES_URL = "https://github.com/ffyuhf/Android-Notification/issues"

/** GPL-3.0 许可证页（GitHub 仓库许可证标签页，用户指定链接） */
private const val GPL_LICENSE_URL =
    "https://github.com/ffyuhf/Android-Notification?tab=GPL-3.0-1-ov-file#"

/**
 * 关于与反馈页面（新增 2026-08-18 23:21 | 历史块边缘与关于反馈页）
 *
 * 独立二级页面（MainActivity 页状态 3）：底栏收起、顶栏返回箭头，
 * 转场复用全局 AnimatedContent 方向感知水平滑动（设置页→关于页右滑前进）。
 *
 * 内容：
 * 1. 软件图标（启动器背景渐变 + 前景白铃铛矢量组合还原）+ 应用名 + 版本号
 * 2. 发送反馈 → GitHub Issues 页面（用户已确认）
 * 3. 分享应用 → 系统 ACTION_SEND 分享面板（文本 = 应用名 + 项目地址）
 * 4. 开源许可证 → GPL-3.0 标识，跳转 GitHub 仓库许可证标签页（用户指定链接）
 * 5. 项目地址 → GitHub 仓库，浏览器打开
 *
 * @param onBack 返回回调（顶栏返回箭头 / 系统返回手势 / 返回键统一触发）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // 独立二级页面：返回手势/返回键回设置页而非退出应用（模态返回分层契约）
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.about_back_to_settings)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ===== 应用标识区：图标 + 名称 + 版本号 =====
            Spacer(modifier = Modifier.height(32.dp))
            AboutAppIcon()
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            // ===== 操作入口区：反馈 / 分享 / 许可证 / 项目地址 =====
            AboutActionItem(
                icon = Icons.Default.Mail,
                title = stringResource(R.string.about_send_feedback),
                subtitle = "GitHub Issues",
                onClick = { openUrl(context, ISSUES_URL) }
            )
            AboutActionItem(
                icon = Icons.Default.Share,
                title = stringResource(R.string.about_share_app),
                onClick = { shareApp(context, context.getString(R.string.app_name)) }
            )
            AboutActionItem(
                icon = Icons.Default.Copyright,
                title = stringResource(R.string.about_open_source_license),
                subtitle = stringResource(R.string.about_license_name),
                onClick = { openUrl(context, GPL_LICENSE_URL) }
            )
            AboutActionItem(
                icon = Icons.Default.Code,
                title = stringResource(R.string.about_project_url),
                subtitle = PROJECT_URL,
                onClick = { openUrl(context, PROJECT_URL) }
            )
            // 底部安全留白（避开手势导航区）
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 应用图标展示（新增 2026-08-18 23:21）
 *
 * 修正（2026-08-19 14:45 | 关于图标与历史二级菜单）：
 * 1. 遮罩外形：22dp 圆角方 → 圆形，与用户启动器圆形图标外形一致
 * 2. 前景比例：原 108dp 画布整体缩至 96dp 全幅显示，铃铛仅占 44.4%；
 *    而启动器仅渲染画布中心 72dp 安全区（铃铛占显示尺寸 66.7%），
 *    铃铛视觉偏小约 1/3。现画布放大至 144dp（= 96 × 108/72）居中
 *    放置，经 96dp 圆形中心裁剪等效启动器安全区渲染，铃铛（放大后
 *    64dp < 96dp 直径）完整显示且比例与启动器严格一致
 *    （mipmap 自适应图标 xml 无法直接 painterResource，故按图层组合绘制）。
 */
@Composable
private fun AboutAppIcon() {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
    ) {
        // 双图层 144dp 居中：背景全幅渐变裁剪后仅见中心 2/3 对角逐变，方向不变
        Image(
            painter = painterResource(R.drawable.ic_launcher_background),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .size(144.dp)
        )
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier
                .align(Alignment.Center)
                .size(144.dp)
        )
    }
}

/**
 * 关于页操作入口列表项（MD3 ListItem 惯例，与设置页样式一致）
 *
 * @param icon 行首语义图标
 * @param title 入口标题
 * @param subtitle 可选副标题（如 GitHub Issues / GPL-3.0 / 项目 URL）
 * @param onClick 点击回调
 */
@Composable
private fun AboutActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { subtitle?.let { Text(it) } },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/**
 * 打开外部网页链接
 *
 * 无浏览器可处理 ACTION_VIEW 时 Toast 兜底提示（避免静默失败）；
 * 不依赖 resolveActivity（Android 11+ 包可见性限制下不可靠）。
 *
 * @param context 上下文
 * @param url 目标链接
 */
private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.about_no_browser, Toast.LENGTH_SHORT).show()
    }
}

/**
 * 系统分享面板分享应用（文本 = 应用名 + 项目地址）
 *
 * @param context 上下文
 * @param appName 应用名称（跟随语言设置）
 */
private fun shareApp(context: Context, appName: String) {
    val shareText = context.getString(R.string.about_share_text, appName, PROJECT_URL)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(shareIntent, null))
}
