# Android-Notification

[![Android CI Build](https://github.com/ffyuhf/Android-Notification/actions/workflows/build.yml/badge.svg)](https://github.com/ffyuhf/Android-Notification/actions/workflows/build.yml)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)

[English](README-en.md)

原生 Android 通知管理应用：自由创建图文通知，支持即时或定时发送、通知栏持久固定、防删除恢复与完整历史记录管理。基于 Kotlin + Jetpack Compose（Material Design 3）构建，零第三方图片库、零网络权限。

<div align="center" style="display: flex; gap: 20px; justify-content: center; flex-wrap: wrap;">
  <img src="https://img.cdn1.vip/i/6a884fbc00e0b_1787318204.webp" alt="图片一" style="width: 220px; border-radius: 12px;">
  <img src="https://img.cdn1.vip/i/6a8850e0bb095_1787318496.webp" alt="图片二" style="width: 220px; border-radius: 12px;">
  <img src="https://img.cdn1.vip/i/6a8850df98540_1787318495.webp" alt="图片三" style="width: 220px; border-radius: 12px;">
</div>



## 功能

- **图文通知**：自定义标题 / 正文 / 图片，大图以 BigPictureStyle 原比例展示
- **定时发送**：精确闹钟调度，日期时间级联选择，未来时间校验
- **防删除恢复**：通知被划掉自动恢复，前台服务周期巡检差异补发
- **通知栏操作**：复制 / 编辑 / 取消固定快捷按钮（可在设置中开关）
- **开机自启**：设备重启后自动恢复固定通知
- **历史管理**：瀑布流卡片布局、搜索、多选批量发送 / 删除、重发可选是否响铃
- **现代界面**：Material Design 3、深色模式、Android 12+ 动态取色、中英双语
- **日志系统**：分级日志落盘、崩溃黑匣子、按级别导出（零存储权限）

## 构建

环境要求：JDK 17、Android SDK（compileSdk 36，最低支持 Android 8.0 / API 26）

```bash
# Debug 构建
./gradlew assembleDebug

# Release 构建
./gradlew assembleRelease
```
## 许可证

[GPL-3.0](LICENSE)
