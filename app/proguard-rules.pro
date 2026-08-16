# ProGuard/R8 混淆规则
# Android 通知应用（新建 2026-08-16 10:45 | B8）
# 修复：build.gradle.kts 引用本文件但此前不存在，导致 release 构建失败

# ===== Room =====
# Room 生成的实现类与实体通过反射访问，保留必要结构
-dontwarn androidx.room.paging.**

# ===== DataStore =====
-dontwarn androidx.datastore.datastore.**

# ===== Kotlin 协程 =====
# kotlinx-coroutines 内部使用 proguard 规则自带发布，无需额外配置
-dontwarn kotlinx.coroutines.**

# ===== 通用保护 =====
# 保留源码行号便于崩溃定位（配合 build.gradle.kts 的 mapping 混淆表）
-keepattributes SourceFile,LineNumberTable

# 崩溃堆栈重映射支持
-renamesourcefileattribute SourceFile
