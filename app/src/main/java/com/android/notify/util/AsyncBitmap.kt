package com.android.notify.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Compose 异步位图加载工具
 *
 * 职责：将 ImageStorageHelper.decodeSampledBitmap 的磁盘 IO + 位图解码
 * 从主线程 composition 中移出（produceState + Dispatchers.IO），
 * 供首页预览、编辑页预览、历史列表缩略图共用，替代原先 4 处
 * `remember { decodeSampledBitmap(...) }` 的主线程同步解码。
 *
 * 修正背景（2026-08-16 15:39 | 图片通知闪退修复）：
 * 原实现在主线程做解码，叠加解码采样率 off-by-one 缺陷后，
 * 大图场景主线程分配数十 MB 触发 OOM 闪退；本工具与算法修正配合根治。
 *
 * 新增（2026-08-16 15:39 | 图片通知闪退修复与日志导出）
 */
object AsyncBitmap {

    /**
     * 异步解码私有目录图片并转为 ImageBitmap
     *
     * 实现思路：produceState 以 (path, maxDimension) 为 key，组合期间 value 为 null
     * （调用方按 null 不渲染处理，占位高度由外层布局保持，避免加载闪烁跳动）；
     * 后台 IO 线程完成下采样解码后回填状态触发重组。path 变化时旧加载自动取消重启。
     *
     * @param path 图片绝对路径；null/空串时状态恒为 null
     * @param maxDimension 解码目标边长上限（px），由调用方按展示场景指定
     * @return 状态对象：解码成功为 ImageBitmap，加载中/失败/无路径为 null
     */
    @Composable
    fun rememberSampledBitmap(path: String?, maxDimension: Int): State<ImageBitmap?> {
        return produceState<ImageBitmap?>(initialValue = null, path, maxDimension) {
            if (path.isNullOrBlank()) {
                value = null
                return@produceState
            }
            value = withContext(Dispatchers.IO) {
                ImageStorageHelper.decodeSampledBitmap(path, maxDimension)
            }?.asImageBitmap()
        }
    }
}
