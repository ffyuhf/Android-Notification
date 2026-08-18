package com.android.notify.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * MD3 浅色配色方案
 */
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1B6B4A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA4F5C8),
    onPrimaryContainer = Color(0xFF002112),
    secondary = Color(0xFF4E6355),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD0E8D6),
    onSecondaryContainer = Color(0xFF0B1F15),
    tertiary = Color(0xFF3B6470),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBFE9F7),
    onTertiaryContainer = Color(0xFF001F27),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBFDF8),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFFBFDF8),
    onSurface = Color(0xFF191C1A),
    // MD3 重绘（2026-08-18 15:52 | 界面MD3全面重绘）：补充 surfaceContainer 系列色槽，
    // 未指定时回落默认中性色与自定义绿色 surface 不协调；以下取值按本项目绿色调
    // surface(0xFFFBFDF8) 为锚点派生，保证卡片/顶栏/底部栏层级色与主题一致
    surfaceDim = Color(0xFFDBDED7),
    surfaceBright = Color(0xFFFBFDF8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F7F2),
    surfaceContainer = Color(0xFFEFF1EB),
    surfaceContainerHigh = Color(0xFFE9EBE5),
    surfaceContainerHighest = Color(0xFFE3E5DF),
    surfaceVariant = Color(0xFFDDE5DB),
    onSurfaceVariant = Color(0xFF414942),
    outline = Color(0xFF717971),
    outlineVariant = Color(0xFFC1C9BF),
    inverseSurface = Color(0xFF2E312D),
    inverseOnSurface = Color(0xFFEFF2EB),
    inversePrimary = Color(0xFF88D8AD),
)

/**
 * MD3 深色配色方案
 */
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF88D8AD),
    onPrimary = Color(0xFF003822),
    primaryContainer = Color(0xFF005235),
    onPrimaryContainer = Color(0xFFA4F5C8),
    secondary = Color(0xFFB4CCBA),
    onSecondary = Color(0xFF203529),
    secondaryContainer = Color(0xFF374B3F),
    onSecondaryContainer = Color(0xFFD0E8D6),
    tertiary = Color(0xFFA3CDDB),
    onTertiary = Color(0xFF033641),
    tertiaryContainer = Color(0xFF214C58),
    onTertiaryContainer = Color(0xFFBFE9F7),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF191C1A),
    onBackground = Color(0xFFE1E3DE),
    surface = Color(0xFF191C1A),
    onSurface = Color(0xFFE1E3DE),
    // MD3 重绘（2026-08-18 15:52 | 界面MD3全面重绘）：深色侧同套补充，
    // 以深色 surface(0xFF191C1A) 为锚点派生容器层级色
    surfaceDim = Color(0xFF111411),
    surfaceBright = Color(0xFF373A36),
    surfaceContainerLowest = Color(0xFF131614),
    surfaceContainerLow = Color(0xFF1B1E1C),
    surfaceContainer = Color(0xFF1F2220),
    surfaceContainerHigh = Color(0xFF292C29),
    surfaceContainerHighest = Color(0xFF343634),
    surfaceVariant = Color(0xFF414942),
    onSurfaceVariant = Color(0xFFC1C9BF),
    outline = Color(0xFF8B938A),
    outlineVariant = Color(0xFF414942),
    inverseSurface = Color(0xFFE1E3DE),
    inverseOnSurface = Color(0xFF2E312D),
    inversePrimary = Color(0xFF1B6B4A),
)

/**
 * MD3 形状基线（2026-08-18 15:52 | 界面MD3全面重绘）
 *
 * 显式固化 Material 3 标准圆角阶梯，全应用卡片/输入框/弹层统一引用，
 * 后续如需调整圆角只改此处即可全局生效。
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * 应用主题 Composable
 *
 * 修复颜色切换卡顿：
 * 1. 使用 remember 缓存 colorScheme，仅在 darkMode 变化时重新计算
 * 2. 使用 DisposableEffect 替代 SideEffect，仅在 darkTheme 变化时更新窗口属性
 * 3. 避免 SideEffect 在每次重组时都触发窗口操作
 *
 * @param darkMode 深色模式设置："system"/"light"/"dark"
 * @param content 子内容
 */
@Composable
fun NotifyAppTheme(
    darkMode: String = "system",
    content: @Composable () -> Unit
) {
    val darkTheme = when (darkMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    // 使用 remember 缓存 colorScheme，仅 darkTheme 变化时重新创建
    val colorScheme = remember(darkTheme) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                // 动态颜色需要 Context，此处无法在 remember 中获取
                // 改为在下方使用 remember(darkTheme) + LocalContext
                null
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }
    }

    // Android 12+ 动态颜色需要 Context
    val finalColorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        remember(darkTheme, context) {
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
    } else {
        colorScheme!!
    }

    // 仅在 darkTheme 变化时更新窗口属性，避免每次重组都触发
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(darkTheme) {
            val window = (view.context as Activity).window
            window.statusBarColor = finalColorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            onDispose { }
        }
    }

    MaterialTheme(
        colorScheme = finalColorScheme,
        // MD3 重绘（2026-08-18 15:52）：统一形状基线，全局圆角一致
        shapes = AppShapes,
        content = content
    )
}
