package com.android.notify.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * 图片存储工具类
 *
 * 负责通知图片的持久化与解码：
 * - copyToPrivateStorage：将 Photo Picker 返回的临时授权 URI 复制到应用私有目录
 *   （Photo Picker URI 的授权在进程结束后失效，巡检恢复/定时触发等场景
 *   重新发送通知时必须读取私有路径，故必须落盘）
 * - deleteImage：删除记录时同步清理图片文件，防止私有目录膨胀
 * - decodeSampledBitmap：两阶段下采样解码，限制目标边长控制内存占用（防 OOM）
 *
 * 新增（2026-08-16 | 图片通知）
 * 修正（2026-08-16 15:39 | 图片通知闪退修复）：
 * - 采样率算法 off-by-one：原条件先除以 (sampleSize*2) 再比较，导致采样率始终
 *   比正确值小一档（4032×3024 照片 @2048 目标 sampleSize 停留在 1，全尺寸解码约
 *   46MB，为设计内存 4 倍），发送大图与启动全量恢复两路径连锁 OOM 闪退；
 *   现改为以当前档尺寸比较，保证解码边长 ≤ 目标值
 * - 追加 OutOfMemoryError 捕获（Error 不被 catch(Exception) 捕获，原防线失效）
 * - 全方法接入 AppLogger 埋点，供设置页分级别导出
 */
object ImageStorageHelper {

    /** 图片存储子目录名（位于 filesDir 下） */
    private const val IMAGE_DIR_NAME = "notification_images"

    /** 通知大图/缩略图统一解码目标边长上限（px） */
    private const val MAX_DECODE_DIMENSION = 2048

    /** 图片 MIME 前缀校验 */
    private const val IMAGE_MIME_PREFIX = "image/"

    /**
     * 获取图片存储目录（不存在时创建）
     *
     * @param context 上下文
     * @return 存储目录 File
     */
    private fun imageDir(context: Context): File {
        val dir = File(context.filesDir, IMAGE_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * 将 Photo Picker 选中的图片复制到应用私有目录
     *
     * 复制后原 URI 的临时授权失效不影响本应用后续使用。
     *
     * @param context 上下文
     * @param uri 源图片 URI（Photo Picker 返回，零权限）
     * @return 复制后的绝对路径；MIME 非图片或复制失败返回 null
     */
    fun copyToPrivateStorage(context: Context, uri: Uri): String? {
        return try {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri)
            // MIME 校验：仅接受图片类型
            if (mimeType == null || !mimeType.startsWith(IMAGE_MIME_PREFIX)) {
                AppLogger.w(TAG, "复制图片失败：MIME 非图片类型（$mimeType）")
                return null
            }

            // 由源文件名推断扩展名；无法获取时按 MIME 兜底，再兜底 .jpg
            val extension = queryDisplayNameExtension(context, uri)
                ?: mimeToExtension(mimeType)
                ?: "jpg"

            val fileName = "${System.currentTimeMillis()}.$extension"
            val targetFile = File(imageDir(context), fileName)

            resolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: run {
                AppLogger.w(TAG, "复制图片失败：无法打开源 URI 输入流")
                return null
            }

            AppLogger.i(TAG, "图片已复制到私有目录：$fileName")
            targetFile.absolutePath
        } catch (e: Exception) {
            AppLogger.w(TAG, "复制图片异常", e)
            null
        }
    }

    /**
     * 删除私有目录中的图片文件（幂等）
     *
     * @param path 图片绝对路径；null 或文件不存在时无副作用
     */
    fun deleteImage(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching {
            File(path).delete()
            AppLogger.d(TAG, "已删除图片文件：$path")
        }
    }

    /**
     * 两阶段下采样解码本地图片
     *
     * 阶段一：仅读取边界信息计算采样率；阶段二：按采样率解码。
     * 目标边长上限 MAX_DECODE_DIMENSION，防止大图解码 OOM。
     *
     * 修正（2026-08-16 15:39 | 图片通知闪退修复）：
     * - 采样率条件以"当前档解码尺寸"比较（原实现误用 sampleSize*2 预降档比较，
     *   实际解码边长可达目标的 2 倍、内存 4 倍，为图片通知双路径闪退根因之一）
     * - 追加 OutOfMemoryError 捕获（Error 类型逃逸原 catch(Exception) 防线）
     *
     * 注意：本方法含磁盘 IO 与大块内存分配，调用方必须处于后台线程
     * （通知链路由 sendNotification 的 withContext(IO) 保证，UI 层经
     * rememberSampledBitmap 的 Dispatchers.IO 保证）。
     *
     * @param path 图片绝对路径
     * @param maxDimension 目标边长上限（px），默认 2048
     * @return 解码后的 Bitmap；文件缺失/解码失败/OOM 返回 null（调用方回退文本样式）
     */
    fun decodeSampledBitmap(path: String?, maxDimension: Int = MAX_DECODE_DIMENSION): Bitmap? {
        if (path.isNullOrBlank()) return null
        return try {
            // 阶段一：读取边界
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, boundsOptions)
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                AppLogger.w(TAG, "解码失败：无法读取图片边界（$path）")
                return null
            }

            // 计算采样率：2 的幂。以"当前 sampleSize 下的解码边长"与目标比较：
            // 只要当前档仍超目标即继续加倍，循环结束时解码边长必然 ≤ maxDimension
            var sampleSize = 1
            while (boundsOptions.outWidth / sampleSize > maxDimension ||
                boundsOptions.outHeight / sampleSize > maxDimension
            ) {
                sampleSize *= 2
            }

            // 阶段二：按采样率解码
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeFile(path, decodeOptions)
            if (bitmap != null) {
                AppLogger.d(
                    TAG,
                    "解码成功 ${bitmap.width}x${bitmap.height}（sampleSize=$sampleSize, " +
                        "源${boundsOptions.outWidth}x${boundsOptions.outHeight}）"
                )
            } else {
                AppLogger.w(TAG, "解码返回空位图（$path）")
            }
            bitmap
        } catch (e: OutOfMemoryError) {
            // OOM 属 Error，原 catch(Exception) 无法拦截（本次闪退根因之二）：
            // 此分支不做大内存分配，仅记日志后返回 null 走文本回退样式
            AppLogger.e(TAG, "解码 OOM（maxDimension=$maxDimension）：$path", e)
            null
        } catch (e: Exception) {
            AppLogger.w(TAG, "解码异常（$path）", e)
            null
        }
    }

    /**
     * 从 URI 查询源文件名并提取扩展名
     *
     * @param context 上下文
     * @param uri 源 URI
     * @return 小写扩展名（不含点）；无法获取返回 null
     */
    private fun queryDisplayNameExtension(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex < 0) return null
                val displayName = cursor.getString(nameIndex) ?: return null
                displayName.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.lowercase()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * MIME 类型到扩展名映射
     *
     * @param mimeType MIME 类型（如 image/png）
     * @return 扩展名（不含点）；未知类型返回 null
     */
    private fun mimeToExtension(mimeType: String): String? {
        return when (mimeType.lowercase()) {
            "image/png" -> "png"
            "image/jpeg" -> "jpg"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/bmp" -> "bmp"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            else -> null
        }
    }

    /** 日志标签（AppLogger 埋点统一使用） */
    private const val TAG = "ImageStorageHelper"
}
