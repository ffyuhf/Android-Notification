package io.github.ffyuhf.notify.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ffyuhf.notify.util.AsyncBitmap

/**
 * 自适应图片组件
 *
 * 职责：按图片自身宽高比完整显示本地私有目录图片，替代「固定高度 + ContentScale.Crop」
 * 的裁剪显示方式（Crop 会放大填满容器后裁掉超出部分，竖图/横图/长图均被截短）。
 *
 * 实现思路：
 * - 复用 AsyncBitmap.rememberSampledBitmap 异步解码（磁盘 IO 不阻塞主线程）
 * - `aspectRatio(bitmap.width / bitmap.height)` 按图片真实比例自适应容器高度，
 *   常规比例图片高度 = 宽度 ÷ 宽高比，完整显示不裁剪
 * - `heightIn(max = maxHeight)` 限高兜底：极端长图（长截图）计算高度超限时高度被钳制，
 *   容器比例与图片比例不一致，配合 ContentScale.Fit 等比完整显示（两侧留白），
 *   防止长图占满整屏；`aspectRatio` 与 `heightIn` 组合时 Compose 保证不超过 maxHeight
 * - `ContentScale.Fit` 恒等完整显示：任何容器尺寸下图片都不被裁剪
 * - 组件内 BoxWithConstraints 自动测量实际显示区物理像素（含屏幕密度），
 *   按显示区 fit 解码实现 1:1 物理像素显示（视觉原图），解码内存不超过
 *   屏幕物理分辨率，OOM 防线保持
 * - 圆角 clip 与卡片视觉风格一致
 *
 * @param path 图片绝对路径；null/空串/解码失败时本组件不渲染任何内容
 * @param maxHeight 图片显示最大高度（dp），防止极端长图占满整屏
 * @param cornerRadius 圆角半径（dp）
 * @param contentDescription 无障碍内容描述
 */
@Composable
fun AdaptiveImage(
    path: String?,
    maxHeight: Dp,
    cornerRadius: Dp,
    contentDescription: String
) {
    BoxWithConstraints {
        // 解码目标 = 实际显示区物理像素（constraints 单位即 px，无需密度换算）
        val displayWidthPx = constraints.maxWidth
        val displayHeightPx = with(LocalDensity.current) { maxHeight.roundToPx() }

        // 异步解码：加载中/失败 value 为 null，不渲染图片区
        val bitmap by AsyncBitmap.rememberSampledBitmap(path, displayWidthPx, displayHeightPx)
        bitmap?.let { imageBitmap ->
            Image(
                bitmap = imageBitmap,
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxWidth()
                    // 按图片真实宽高比自适应高度；ratio 恒为正数（bitmap 尺寸必大于 0）
                    .aspectRatio(imageBitmap.width.toFloat() / imageBitmap.height)
                    // 限高兜底：超限时高度钳制到 maxHeight，Fit 保证完整显示（两侧留白）
                    .heightIn(max = maxHeight)
                    .clip(RoundedCornerShape(cornerRadius)),
                // Fit：任何容器尺寸下图片等比完整显示，不裁剪
                contentScale = ContentScale.Fit
            )
        }
    }
}
