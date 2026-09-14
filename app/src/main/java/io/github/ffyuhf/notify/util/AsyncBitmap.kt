package io.github.ffyuhf.notify.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Compose 异步位图加载工具
 *
 * 将 ImageStorageHelper 的磁盘 IO + 位图解码从主线程 composition 中移出，
 * 供首页预览、编辑页预览、历史列表缩略图共用。
 *
 * 采用 LaunchedEffect + remember { mutableStateOf } 模式而非 produceState：
 * 本项目 lint 环境下 produceState 持续触发 ProduceStateDoesNotAssignValue
 * Error（Compose lint 已知误报），二者行为等价。
 */
object AsyncBitmap {

    /**
     * 异步解码私有目录图片并转为 ImageBitmap（fit 到显示框）
     *
     * remember 持有可空位图状态；LaunchedEffect 以
     * (path, targetWidthPx, targetHeightPx) 为键在组合协程中执行 IO 解码并回填状态，
     * 键变化时旧加载自动取消重启；组合期间 value 为 null（调用方按 null
     * 不渲染处理，占位高度由外层布局保持，避免加载闪烁跳动）。
     * 解码目标为显示框物理像素，配合 decodeSampledBitmapToFit 实现 1:1 物理像素显示。
     *
     * @param path 图片绝对路径；null/空串时状态恒为 null
     * @param targetWidthPx 目标显示区宽（物理像素）
     * @param targetHeightPx 目标显示区高（物理像素）
     * @return 状态对象：解码成功为 ImageBitmap，加载中/失败/无路径为 null
     */
    @Composable
    fun rememberSampledBitmap(
        path: String?,
        targetWidthPx: Int,
        targetHeightPx: Int
    ): State<ImageBitmap?> {
        val bitmapState = remember { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(path, targetWidthPx, targetHeightPx) {
            bitmapState.value = loadSampledImageBitmap(path, targetWidthPx, targetHeightPx)
        }
        return bitmapState
    }

    /**
     * IO 线程下采样解码并转换为 ImageBitmap（LaunchedEffect 协程内调用）
     *
     * @param path 图片绝对路径；null/空串返回 null
     * @param targetWidthPx 目标显示区宽（物理像素）
     * @param targetHeightPx 目标显示区高（物理像素）
     * @return 解码成功为 ImageBitmap；无路径/解码失败返回 null
     */
    private suspend fun loadSampledImageBitmap(
        path: String?,
        targetWidthPx: Int,
        targetHeightPx: Int
    ): ImageBitmap? {
        if (path.isNullOrBlank()) return null
        return withContext(Dispatchers.IO) {
            ImageStorageHelper.decodeSampledBitmapToFit(path, targetWidthPx, targetHeightPx)
        }?.asImageBitmap()
    }
}
