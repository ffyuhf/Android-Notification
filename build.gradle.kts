// 根项目构建配置
// 统一管理插件版本，子项目通过 plugins 块引用

plugins {
    alias(libs.plugins.android.application) apply false
    // kotlin.android 插件声明移除（2026-09-13 23:57 | AGP9内置Kotlin迁移，用户裁决 Q1=C）：
    // AGP 9.0 起 Kotlin 支持内置于 AGP，该插件不再应用；compose/ksp 仍需版本登记，保留
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
