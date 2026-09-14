# ProGuard/R8 混淆规则

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
