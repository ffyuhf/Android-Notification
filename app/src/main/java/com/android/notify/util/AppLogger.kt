package com.android.notify.util

import android.content.Context
import android.util.Log
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/**
 * 应用内分级日志工具
 *
 * 职责：
 * 1. 分级记录（DEBUG/INFO/WARN/ERROR），同时镜像输出到 Logcat
 * 2. 持久化到应用私有目录 logs/app_log.txt，供设置页分级别导出（零权限，私有目录直读）
 * 3. 容量控制：文件超过 2MB 时截断保留尾部 1MB，防无限膨胀
 * 4. 崩溃黑匣子：writeCrashBlocking 同步落盘未捕获异常完整堆栈
 *    （崩溃路径进程即将终止，必须同步写入，不走异步队列）
 *
 * 实现要点：
 * - 单线程 executor 串行追加写，调用方（含主线程埋点）仅入队，非阻塞
 * - 行格式固定 "MM-dd HH:mm:ss.SSS L/Tag: message"，级别字符位于行首第 20 列，
 *   readForExport 依据该列过滤级别；无法解析级别的续行（堆栈行）跟随上一行决策
 *
 * 新增（2026-08-16 15:39 | 图片通知闪退修复与日志导出）
 */
object AppLogger {

    /** 日志级别（short：文件行级别字符；priority：数值越大越严重；androidPriority：Logcat 级别） */
    enum class LogLevel(val short: Char, val priority: Int, val androidPriority: Int) {
        DEBUG('D', 0, Log.DEBUG),
        INFO('I', 1, Log.INFO),
        WARN('W', 2, Log.WARN),
        ERROR('E', 3, Log.ERROR);

        companion object {
            /** 由文件行级别字符反查级别；非级别字符返回 null */
            fun fromShort(c: Char): LogLevel? = entries.firstOrNull { it.short == c }
        }
    }

    /** 日志目录名（位于 filesDir 下） */
    private const val LOG_DIR_NAME = "logs"

    /** 日志文件名（单文件追加，配合容量截断，不按日分文件） */
    private const val LOG_FILE_NAME = "app_log.txt"

    /** 文件大小上限：超过即截断（2MB） */
    private const val MAX_FILE_BYTES = 2L * 1024 * 1024

    /** 截断后保留的尾部大小（1MB） */
    private const val KEEP_TAIL_BYTES = 1L * 1024 * 1024

    /** 行时间戳格式：MM-dd HH:mm:ss.SSS（级别字符恰好位于第 20 列，索引 19） */
    private val LINE_TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")

    /** 应用上下文（init 注入；未初始化时仅镜像 Logcat 不落盘） */
    @Volatile
    private var appContext: Context? = null

    /** 单线程串行写队列（守护线程，不阻碍进程退出） */
    private val logExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "app-logger").apply { isDaemon = true }
    }

    /**
     * 初始化（Application.onCreate 调用，仅一次）
     *
     * @param context 应用上下文
     */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // ===== 对外记录接口 =====

    /** 记录 DEBUG 级日志 */
    fun d(tag: String, message: String) = enqueue(LogLevel.DEBUG, tag, message, null)

    /** 记录 INFO 级日志 */
    fun i(tag: String, message: String) = enqueue(LogLevel.INFO, tag, message, null)

    /** 记录 WARN 级日志 */
    fun w(tag: String, message: String, throwable: Throwable? = null) =
        enqueue(LogLevel.WARN, tag, message, throwable)

    /** 记录 ERROR 级日志 */
    fun e(tag: String, message: String, throwable: Throwable? = null) =
        enqueue(LogLevel.ERROR, tag, message, throwable)

    /**
     * 崩溃黑匣子：同步落盘未捕获异常完整堆栈（专用接口，不走异步队列）
     *
     * 进程即将终止，必须立即写入；内部全体容错，任何失败不得中断默认崩溃流程。
     *
     * @param thread 崩溃线程
     * @param throwable 未捕获异常
     */
    fun writeCrashBlocking(thread: Thread, throwable: Throwable) {
        runCatching {
            val file = logFile() ?: return
            val stackTrace = android.util.Log.getStackTraceString(throwable)
            val line = "${nowFormatted()} ${LogLevel.ERROR.short}/CRASH: " +
                "Uncaught exception on thread '${thread.name}'\n$stackTrace\n"
            file.appendText(line)
        }
    }

    /**
     * 读取日志内容用于导出（按级别过滤）
     *
     * 调用方应在 IO 线程执行（ViewModel exportLink 内 withContext(Dispatchers.IO)）。
     *
     * @param minLevel 最低级别：null 返回全部；否则仅返回该级别及以上的行
     *                 （含其后续堆栈续行）；无法解析级别的行归入"全部"导出
     * @return 过滤后的日志文本；无文件或读取失败返回空串
     */
    fun readForExport(minLevel: LogLevel?): String {
        val file = logFile() ?: return ""
        val lines = try {
            file.readLines()
        } catch (e: Exception) {
            return ""
        }
        if (minLevel == null) return lines.joinToString("\n")

        val result = StringBuilder()
        // keepContinuation：当前主行命中级别时，其后续堆栈续行一并保留
        var keepContinuation = false
        for (line in lines) {
            val lineLevel = line.getOrNull(19)?.let { LogLevel.fromShort(it) }
            if (lineLevel != null) {
                keepContinuation = lineLevel.priority >= minLevel.priority
            }
            if (keepContinuation) {
                result.appendLine(line)
            }
        }
        return result.toString()
    }

    /**
     * 启动时容量清理：文件超过上限则截断保留尾部（在写线程执行，不阻塞主线程）
     */
    fun trimIfNeededAsync() {
        logExecutor.execute { runCatching { trimFileIfNeeded() } }
    }

    // ===== 内部实现 =====

    /** 入队一条日志：镜像 Logcat + 异步追加文件 */
    private fun enqueue(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        val throwableText = throwable?.let { "\n${android.util.Log.getStackTraceString(it)}" } ?: ""
        // 镜像 Logcat（即使未 init 也可用）
        Log.println(level.androidPriority, tag, message + throwableText)

        if (appContext == null) return
        val line = "${nowFormatted()} ${level.short}/$tag: $message$throwableText"
        logExecutor.execute {
            runCatching {
                val file = logFile() ?: return@execute
                file.appendText(line + "\n")
                trimFileIfNeeded()
            }
        }
    }

    /** 获取日志文件（目录不存在时创建）；未初始化返回 null */
    private fun logFile(): File? {
        val context = appContext ?: return null
        val dir = File(context.filesDir, LOG_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, LOG_FILE_NAME)
    }

    /** 当前时间格式化为行时间戳 */
    private fun nowFormatted(): String = LocalDateTime.now().format(LINE_TIME_FORMAT)

    /**
     * 文件容量截断：超过 MAX_FILE_BYTES 时保留尾部 KEEP_TAIL_BYTES
     *
     * 实现思路：按字节定位截断点后，向后查到首个换行符（避免截断在多字节字符
     * 或行中间），从该行首开始保留尾部内容，头部丢弃。
     */
    private fun trimFileIfNeeded() {
        val file = logFile() ?: return
        if (file.length() <= MAX_FILE_BYTES) return

        val bytes = file.readBytes()
        // 整改（2026-08-16 16:07 | CI 编译失败）：KEEP_TAIL_BYTES 为 Long，
        // 原表达式使 cutIndex 推断为 Long，数组索引/copyOfRange 需要 Int；
        // 先转 Int 再参与下标运算（1MB 在 Int 范围内）
        val cutIndex = (bytes.size - KEEP_TAIL_BYTES.toInt()).coerceAtLeast(0)
        // 向后查找首个换行符，从下一行行首开始保留
        var lineStart = cutIndex
        while (lineStart < bytes.size && bytes[lineStart] != '\n'.code.toByte()) {
            lineStart++
        }
        if (lineStart < bytes.size) lineStart++ // 跳过换行符本身

        val kept = bytes.copyOfRange(lineStart, bytes.size)
        file.writeBytes(kept)
    }
}
