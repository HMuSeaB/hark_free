# HarkFree · 纯净开源 Android 多账号与导出客户端

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)

> **HarkFree** 是一个针对 **Hark AI (`https://hark.com`)** 的原生 Android 多账号隔离容器、微信风格排版优化与对话导出客户端。
> 采用**净室重写（Clean-room Rewrite）**打造，100% 免费开源，零多余权限，零后门，无任何卡密收费。

---

## 一、 功能亮点

- **多进程账号隔离（Multi-Process Slots）**：提供 4 个独立的物理子进程（`:slot0` ~ `:slot3`），独立拥有 WebView 存储区与 Cookie，彻底杜绝串号风险。
- **一键导出 Markdown**：点击顶部「导出MD」，自动递归抓取当前会话的全部历史记录与附件链接，高保真格式化为 Markdown 文件，并原生保存到手机存储 `Download/HarkFree/` 目录中。
- **微信风格排版切换（WeChat Style UI）**：一键切换沉浸式微信风格，对话气泡采用经典微信绿（`#95EC69`）与白底，优化文字对比度与头像展示。
- **极致纯净与轻量**：
  - 剔除了市面上套壳 APK 夹带的 37.6MB Cloudflare 穿透工具与 PRoot 虚拟机，纯原生 Java 构建，编译产物仅 **~2MB**。
  - 仅申请基础 `INTERNET` 权限，不申请相机、录音、定位或系统提权。

---

## 二、 目录结构

```text
hark_free/
├── app/
│   ├── build.gradle                          # App 模块构建配置
│   ├── proguard-rules.pro                   # 混淆规则
│   └── src/main/
│       ├── AndroidManifest.xml              # 清单配置（声明多进程槽位）
│       ├── assets/
│       │   ├── export_engine.js             # 无损 Markdown 递归抓取与生成引擎
│       │   └── wechat_style.css             # 微信风格绿色气泡排版样式表
│       ├── java/com/free/hark/
│       │   ├── MainActivity.java            # 主控制台（槽位选择与分发）
│       │   ├── SlotActivity.java            # 独立进程槽位 WebView 容器
│       │   └── HarkNativeBridge.java        # 原生安全通信桥与 MediaStore 下载保存
│       └── res/                             # 界面布局、色彩、主题与徽章资源
├── build.gradle                             # 根项目构建脚本
├── settings.gradle                          # 模块管理与镜像源配置
├── gradle.properties                        # JVM 内存与 AndroidX 配置
├── local.properties                         # SDK 本地路径
├── LICENSE                                  # MIT 开源协议
└── README.md                                # 项目说明文档
```

---

## 三、 快速构建与安装指南

你可以选择使用 **Android Studio 界面** 或 **Windows 终端命令行** 进行编译构建：

### 方式一：使用 Android Studio 编译（推荐）

1. 打开 **Android Studio**；
2. 选择 **Open**，导航到本目录：`d:\4rchive\Code\anti-ads\hark_free` 并打开；
3. 等待 Gradle 依赖同步完成；
4. 点击顶部菜单栏 **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**；
5. 构建完成后，点击右下角出现的 `locate` 链接，即可获得生成好的 `app-debug.apk`，传输至手机安装即可。

### 方式二：使用 Windows 终端命令行编译

在当前项目的根目录下运行 Windows PowerShell：

```powershell
# 1. 切换至项目目录
cd d:\4rchive\Code\anti-ads\hark_free

# 2. 如果系统已配置 Gradle，直接执行构建任务
gradle assembleRelease

# 或使用调试包构建
gradle assembleDebug
```
构建出的 APK 位于：`d:\4rchive\Code\anti-ads\hark_free\app\build\outputs\apk\debug\app-debug.apk`。

通过 ADB 命令行直接安装到已连接的手机：
```powershell
adb install -r d:\4rchive\Code\anti-ads\hark_free\app\build\outputs\apk\debug\app-debug.apk
```

---

## 四、 安全性说明与免责声明

1. **隐私安全**：本软件不收集任何个人凭据、Cookie 或会话内容。所有的账号登录凭据直接保存在手机本地系统的 WebView 沙盒中，与官方网页登录机制完全一致。
2. **免责声明**：HarkFree 为开源社区独立作品，非 Hark 官方产品，亦与官方没有任何隶属关系。Hark 及其相关商标归其合法持有人所有。请在遵守平台服务条款的前提下合理使用。
