# SAMC CheckForm Modern

这是旧 Tkinter 程序的跨平台重构版本，目标平台：

- Windows：`.exe` / `.msi`，用户级安装，可从系统卸载
- Android：`.apk`，支持浅色和深色主题
- macOS：`.app` / `.dmg` / `.pkg`

## 技术方案

- Kotlin Multiplatform：共享检查规则和数据模型
- Compose Multiplatform：桌面与 Android 共享现代界面
- 保留原 Python 的常规活动、非常规活动、文件名、必填项、比例、评分和总分校验逻辑
- 界面采用半透明卡片、柔和渐变、圆角、浅色/深色主题，避免复刻旧 Tkinter 页面

## 构建环境

需要安装：

1. JDK 17 或更高版本
2. Gradle 8.9 或使用项目 Gradle Wrapper
3. Android SDK 35（只构建 APK 时需要）
4. macOS + Xcode Command Line Tools（构建 macOS 安装包时需要）


Windows 安装包会带有卸载入口；Android 使用系统应用卸载；macOS 使用 `.app` 删除或 `.pkg` 的系统安装方式。

## 当前状态

共享核心和 UI 已迁移到 `shared`、`composeApp`。Excel 文件读取、批注写回和文件夹选择需要继续接入平台文件 API；当前 UI 的“预览界面”用于先检查新的视觉方向，不代表旧 Tkinter 界面。
