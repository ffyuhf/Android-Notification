// App 模块构建配置：Android 通知应用主模块

plugins {
    alias(libs.plugins.android.application)
    // 不应用 kotlin.android 插件：AGP 9.0 起 Kotlin 支持内置于 AGP，
    // 显式应用该插件即致命冲突（见 Google 官方迁移指南 migrate-to-built-in-kotlin）
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
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
            // 签名方案 v1(JAR)+v2(APK Signature Scheme)+v3(密钥轮换) 三方案全开：
            // 覆盖 minSdk 26 至最新全版本验签；与可重复构建兼容（同输入+同密钥产出一致）
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

// Room schema 导出目录：exportSchema = true 时必须配置导出位置，
// schema 纳入版本管理用于迁移校验
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// 可重复构建：依赖锁定。gradle.lockfile 经 CI write-locks job 生成后自动提交仓库；
// STRICT 模式下依赖解析与 lockfile 不一致即构建失败，消除传递依赖随时间漂移的不确定性
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.STRICT
}

// 依赖锁全量落盘任务（本地调试工具，CI 写锁走真实构建+--write-locks，非本任务）：
// 遍历本模块全部可解析配置逐个强制依赖图解析，配合 --write-locks 一次性为所有配置
// 写入锁状态。注意两类已实证的局限：① dependencies 报告任务的解析路径在本栈
// （Gradle 9.7.1 + AGP 9.4.0）退出码 0 却不落盘锁状态；② 本任务对 AGP 惰性配置
// （如 androidApis，assembleDebug 任务图 realize 时才可见）覆盖不到，
// 图解析遍历集合 ≠ 构建所需集合
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
            // 图解析写锁：it.resolve() 会继续做 artifact 文件选择，而 androidTest classpath
            // 内含被测项目 :app 自身依赖（application 组件，无 library 类别变体）→ artifact
            // 变体选择必然歧义；lockfile 记录的是版本图结果，访问
            // incoming.resolutionResult.root 仅触发依赖图解析即完成写锁，不做 artifact 选择
            // （Gradle 官方 API：getResolutionResult "will resolve the dependency graph
            // but will not resolve or download the artifacts"）
            it.incoming.resolutionResult.root
        }
    }
}

// 可重复构建：JVM 工具链锚定。任何构建环境（本地/CI）统一以 JDK 25 编译，
// 消除 JDK 版本差异导致的字节码不确定性。
// 注：Android 字节码目标（compileOptions/compilerOptions=17）由 minSdk 与 D8 desugar
// 决定，与构建 JDK 为独立维度，维持 17 不变
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// Kotlin 编译选项：AGP 9 内置 Kotlin 下使用顶层 kotlin { compilerOptions { } } DSL
// （android 块内 kotlinOptions 已弃用且随 kotlin.android 插件移除失去注册方）；
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
    // AppCompat：per-app 语言切换（AppCompatDelegate.setApplicationLocales）
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
