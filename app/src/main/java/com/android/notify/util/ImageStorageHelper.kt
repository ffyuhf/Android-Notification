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
            } ?: return null

            targetFile.absolutePath
        } catch (e: Exception) {
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
        runCatching { File(path).delete() }
    }

    /**
     * 两阶段下采样解码本地图片
     *
     * 阶段一：仅读取边界信息计算采样率；阶段二：按采样率解码。
     * 目标边长上限 MAX_DECODE_DIMENSION，防止大图解码 OOM。
     *
     * @param path 图片绝对路径
     * @param maxDimension 目标边长上限（px），默认 2048
     * @return 解码后的 Bitmap；文件缺失/解码失败返回 null
     */
    fun decodeSampledBitmap(path: String?, maxDimension: Int = MAX_DECODE_DIMENSION): Bitmap? {
        if (path.isNullOrBlank()) return null
        return try {
            // 阶段一：读取边界
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, boundsOptions)
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

            // 计算采样率：2 的幂，使解码尺寸不超过目标边长
            var sampleSize = 1
            while (boundsOptions.outWidth / (sampleSize * 2) >= maxDimension ||
                boundsOptions.outHeight / (sampleSize * 2) >= maxDimension
            ) {
                sampleSize *= 2
            }

            // 阶段二：按采样率解码
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeFile(path, decodeOptions)
        } catch (e: Exception) {
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
}
