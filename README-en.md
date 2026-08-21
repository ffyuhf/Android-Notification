# Android-Notification

[![Android CI Build](https://github.com/ffyuhf/Android-Notification/actions/workflows/build.yml/badge.svg)](https://github.com/ffyuhf/Android-Notification/actions/workflows/build.yml)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)

A native Android notification manager: create text & image notifications, send them instantly or on schedule, keep them pinned, auto-restore on dismissal, and manage everything through a complete history. Built with Kotlin + Jetpack Compose (Material Design 3), with zero third-party image libraries and zero network permissions.

[简体中文](README.md)

<div style="display: flex; gap: 15px; justify-content: center; align-items: center; flex-wrap: wrap;">
  <img src="https://img.cdn1.vip/i/6a884fbc00e0b_1787318204.webp" alt="图片一" style="max-width: 30%; height: auto; border-radius: 8px;">
  <img src="https://img.cdn1.vip/i/6a8850e0bb095_1787318496.webp" alt="图片二" style="max-width: 30%; height: auto; border-radius: 8px;">
  <img src="https://img.cdn1.vip/i/6a8850df98540_1787318495.webp" alt="图片三" style="max-width: 30%; height: auto; border-radius: 8px;">
</div>

## Features

- **Rich notifications**: custom title / text / image, large image shown via BigPictureStyle at original aspect ratio
- **Scheduled sending**: exact-alarm scheduling with cascading date & time pickers and future-time validation
- **Dismiss-proof restore**: auto-restore when a notification is swiped away; foreground service periodically re-pins missing ones
- **Notification actions**: copy / edit / unpin action buttons (togglable in Settings)
- **Boot restore**: pinned notifications automatically restored after device reboot
- **History management**: staggered-grid cards, search, multi-select batch send / delete, resend with optional sound
- **Modern UI**: Material Design 3, dark mode, Android 12+ dynamic color, Chinese & English
- **Logging**: leveled file logging, crash black box, export by level (zero storage permissions)

## Build

Requirements: JDK 17, Android SDK (compileSdk 36, minimum Android 8.0 / API 26)

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

## License

[GPL-3.0](LICENSE)
