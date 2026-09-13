// App 模块构建配置
// Android 通知应用主模块

plugins {
    alias(libs.plugins.android.application)
    // kotlin.android 插件移除（2026-09-13 23:57 | AGP9内置Kotlin迁移）：AGP 9.0 起 Kotlin
    // 支持内置于 AGP，显式应用该插件即致命冲突；来源：CI 错误原文 + Google 官方迁移指南
    // migrate-to-built-in-kotlin + 用户裁决 Q1=C（计划书 AGP9内置Kotlin迁移修复 v1.0）
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    // 包名变更（2026-09-13 | 批次二 A1/A2，来源：用户指令+Q4确认无发布版零兼容负担 2026-09-13 22:49）
    namespace = "io.github.ffyuhf.notify"
    compileSdk = libs.versions.compileSdk.get().toInt()

    // 签名配置：CI 发版时从环境变量读取，变量为空或文件不存在时自动跳过
    signingConfigs {
        create("release") {
            val ksPath = System.getenv("KEYSTORE_PATH")
            if (!ksPath.isNullOrEmpty() && file(ksPath).exists()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
            // 签名方案（新增 2026-09-13 23:22 | 用户指令）：v1(JAR)+v2(APK Signature Scheme)+
            // v3(密钥轮换) 三方案全开，覆盖 minSdk 26 至最新全版本验签；
            // 与可重复构建兼容（同输入+同密钥产出一致）
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    defaultConfig {
        applicationId = "io.github.ffyuhf.notify"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val ksPath = System.getenv("KEYSTORE_PATH")
            if (!ksPath.isNullOrEmpty() && file(ksPath).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // kotlinOptions 块移除（2026-09-13 23:57 | AGP9内置Kotlin迁移）：该 DSL 自 AGP 8.8.0 弃用，
    // 且其扩展随 kotlin.android 插件移除失去注册方；jvmTarget=17 迁移至顶层
    // kotlin { compilerOptions { } }（见下方），与 compileOptions 显式配对

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Room schema 导出目录（优化 2026-08-16 | P9）：
// exportSchema = true 时必须配置导出位置，schema 纳入版本管理用于迁移校验
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// 可重复构建：依赖锁定（新增 2026-09-13 | 批次一 D1，来源：用户确认 Q3选B 2026-09-13 22:49）
// gradle.lockfile 经 CI write-locks job 生成后自动提交仓库；STRICT 模式下依赖解析与
// lockfile 不一致即构建失败，消除传递依赖随时间漂移的不确定性
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.STRICT
}

// 依赖锁全量落盘任务（新增 2026-09-14 00:43 | 依赖锁定Lockfile落盘修复，用户裁决 Q1=A；
// 修正 2026-09-14 01:33 | resolveAndLockAll变体歧义修复，用户裁决 Q1=A）：
// 来源：Gradle 官方 dependency_locking 文档原生模式——遍历本模块全部可解析配置逐个
// 强制依赖图解析写锁（官方 it.resolve() 文件解析模式在 androidTest 配置上必然变体
// 歧义，缘由与修正详见 doLast 内注释），配合 --write-locks 一次性为所有配置写入锁状态；
// 不依赖 dependencies 报告任务的解析路径（该路径在本栈 Gradle 9.7.1 + AGP 9.4.0
// 实测退出码 0 却不落盘锁状态，2026-09-14 CI assembleDebug 失败实证）
tasks.register("resolveAndLockAll") {
    notCompatibleWithConfigurationCache("Filters configurations at execution time")
    doFirst {
        require(gradle.startParameter.isWriteDependencyLocks) {
            "$path 必须在命令行携带 --write-locks 运行"
        }
    }
    doLast {
        configurations.filter {
            // 官方注释位：可在此对锁定配置做过滤，本项目锁定全部
            it.isCanBeResolved
        }.forEach {
            // 图解析写锁（修正 2026-09-14 01:33 | resolveAndLockAll变体歧义修复，用户裁决 Q1=A）：
            // 官方模式的 it.resolve() 会继续做 artifact 文件选择，而 androidTest classpath 内含
            // 被测项目 :app 自身依赖（application 组件，10 个变体均不声明 library 类别）→
            // artifact 变体选择必然歧义（2026-09-14 CI 实证）；lockfile 记录的是版本图结果，
            // 访问 incoming.resolutionResult.root 仅触发依赖图解析即完成写锁，不做 artifact
            // 选择（Gradle 官方 API 文档：getResolutionResult "will resolve the dependency
            // graph but will not resolve or download the artifacts"）
            it.incoming.resolutionResult.root
        }
    }
}

// 可重复构建：JVM 工具链锚定（新增 2026-09-13 | 批次一 D2；用户反馈 JDK 17 太老，
// 升至最新 LTS 25，CI setup-java 与 README 矩阵同步；Gradle 9.7.1 官方支持 JVM 17-26）
// 修改 2026-09-13 23:57 | AGP9内置Kotlin迁移：锚定载体由 KGP kotlin { jvmToolchain(25) } 迁移
// 为 Gradle 原生 java { toolchain }（用户裁决 Q2=B）——任何构建环境（本地/CI）统一以
// JDK 25 编译，消除 JDK 版本差异导致的字节码不确定性，锚定语义不变；
// 注：Android 字节码目标（compileOptions/compilerOptions=17）由 minSdk 与 D8 desugar 决定，
// 与构建 JDK 为独立维度，维持 17 不变
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// Kotlin 编译选项（新增 2026-09-13 23:57 | AGP9内置Kotlin迁移）：android 块内 kotlinOptions
// 自 AGP 8.8.0 弃用且随插件移除失去注册方，按 Google 官方迁移指南 migrate-to-built-in-kotlin
// 迁移为顶层 kotlin { compilerOptions { } } DSL（用户裁决 Q3=B，写法逐字对齐官方指南）；
// jvmTarget 维持 17，与上方 compileOptions（Java 17）显式配对
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    // Compose BOM - 统一管理 Compose 版本
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose UI
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // AndroidX Core
    implementation(libs.core.ktx)
    // AppCompat：per-app 语言切换（AppCompatDelegate.setApplicationLocales）（新增 2026-08-16 | B4）
    implementation(libs.appcompat)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.service)
    implementation(libs.navigation.compose)
    implementation(libs.splashscreen)

    // Room 数据库
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore 偏好设置
    implementation(libs.datastore.preferences)

    // WorkManager 后台任务
    implementation(libs.work.runtime.ktx)

    // 测试
    testImplementation(libs.junit)
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso.core)
}
