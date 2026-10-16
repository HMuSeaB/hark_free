<h1 align="center">HarkFree</h1>

<p align="center">
  <b>An open-source, clean-room reimplementation of the multi-account container & enhancer for Hark AI, built from scratch in pure Android.</b><br>
  Multi-process slot isolation, full conversation Markdown exporter, WeChat-style bubble styling and zero telemetry,<br>
  in a lightweight native app under 2MB. 100% free, open source, and yours.
</p>

<p align="center">
  <img alt="Platform: Android" src="https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-3DDC84?style=flat-square&logo=android&logoColor=white">
  <img alt="Language: 100% Java & Native Android" src="https://img.shields.io/badge/100%25-Native%20Android-07C160?style=flat-square">
  <img alt="License: MIT" src="https://img.shields.io/badge/License-MIT-blue?style=flat-square">
  <img alt="Status: Clean-room Ready" src="https://img.shields.io/badge/Status-Clean--room%20Rewrite-orange?style=flat-square">
  <img alt="Zero Bloat" src="https://img.shields.io/badge/Size-~2MB%20Zero%20Bloat-brightgreen?style=flat-square">
</p>

<p align="center">
  <a href="#features">功能特性</a> ·
  <a href="#why-clean-room">为什么净室重写</a> ·
  <a href="#architecture">多进程隔离架构</a> ·
  <a href="#chat-export">会话无损导出</a> ·
  <a href="#wechat-theme">微信绿沉浸排版</a> ·
  <a href="#get-started">编译与安装</a> ·
  <a href="#security">安全审计与声明</a>
</p>

<br>

<table>
  <tr>
    <td width="25%" valign="top">
      <h3>🎛️ 物理进程级隔离</h3>
      4 个完全独立的子进程（<code>:slot0</code> 到 <code>:slot3</code>），Cookie 与沙盒存储各自物理隔离，彻底杜绝串号。
    </td>
    <td width="25%" valign="top">
      <h3>⚡ 原生纯净，仅 ~2MB</h3>
      纯原生 Android 构建，告别市面上打包了 37.6MB Cloudflare 穿透工具和 PRoot 虚拟机的臃肿套壳，毫秒级启动。
    </td>
    <td width="25%" valign="top">
      <h3>🛡️ 拒绝套路，零卡密</h3>
      100% 免费开源，彻底移除商业套壳软件中用“自动注册小号”伪装成“无限Token”并出售激活码收割用户的套路。
    </td>
    <td width="25%" valign="top">
      <h3>🗂️ 会话无损导出</h3>
      递归自动翻页抓取全部历史对话、代码块与附件链接，格式化为 Markdown 直接原生保存至手机 <code>Download/</code>。
    </td>
  </tr>
</table>

<br>

<h2 id="features">核心功能一览 (Features)</h2>

<table>
  <tr>
    <td width="50%" valign="top">
      <h3>🗂️ 会话无损导出引擎 (Markdown Exporter)</h3>
      告别从浏览器网页里一条条手动复制。点击顶部<b>「导出MD」</b>，内置导出引擎自动遍历 <code>/api/messages/history</code>：
      <ul>
        <li><b>自动翻页合并</b>：自动处理 <code>nextCursor</code>，单会话支持递归拉取上百页历史；</li>
        <li><b>完整元数据</b>：自动标注会话 ID、导出时间戳、角色头像与发言时间；</li>
        <li><b>附件与代码无损</b>：精准提取所有附件名称与官方高速下载链接；</li>
        <li><b>原生保存</b>：基于 Android <code>MediaStore</code> API，一键直存至手机 <code>Download/HarkFree/</code> 文件夹，无需给危险的文件管理权限。</li>
      </ul>
    </td>
    <td width="50%" valign="top">
      <h3>💬 仿微信沉浸式排版 (WeChat Style UI)</h3>
      网页版原生的排版在手机窄屏上阅读吃力。HarkFree 内置一键样式热切换：
      <ul>
        <li><b>经典微信绿气泡</b>：用户发言自动匹配 <code>#95EC69</code> 经典绿底与深色字，AI 发言采用清爽白底；</li>
        <li><b>自动对齐与头像</b>：动态监听 DOM 渲染，自动按消息位置分配用户头像与 Hark 标志头像；</li>
        <li><b>暗黑模式兼容</b>：针对 OLED 屏幕优化对比度，大幅减轻夜间阅读疲劳；</li>
        <li><b>随时还原</b>：顶部按钮一键即时在“官方原生样式”与“微信风格”间无缝切换。</li>
      </ul>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <h3>🔒 物理隔离多开槽位 (Multi-Process Slots)</h3>
      市面上的普通双开往往通过清除 Cookie 或单进程切换，容易发生会话互相踩踏：
      <ul>
        <li>每个槽位声明独立的 <code>android:process=":slotN"</code>；</li>
        <li>Android 操作系统为每个进程分配完全隔离的沙盒目录和内存空间；</li>
        <li>WebView 的 Cookie、LocalStorage、SessionStorage 各自物理独立，登录状态永久保持，互不影响。</li>
      </ul>
    </td>
    <td width="50%" valign="top">
      <h3>🌿 极致纯净，零隐私风险</h3>
      拒绝黑灰产代码，只保留用户真正需要的工具特性：
      <ul>
        <li><b>仅申请 <code>INTERNET</code> 基础权限</b>，零相机、零麦克风、零定位；</li>
        <li><b>不索取 Shizuku 提权</b>，不扫描用户的已安装应用列表；</li>
        <li><b>不常驻后台穿透隧道</b>，退至后台即自动休眠，省电且不发热；</li>
        <li>所有会话数据只在手机与 Hark 官方服务器之间传输，无任何第三方中转。</li>
      </ul>
    </td>
  </tr>
</table>

<br>

<h2 id="why-clean-room">为什么进行净室重写 (Why Clean-room)？</h2>

市面上流传的类似商业套壳（如 HarkMulti）通过宣传“无限 Token”吸引用户，但经深入逆向分析，其存在三大核心硬伤：

<table>
  <thead>
    <tr>
      <th width="20%">对比维度</th>
      <th width="40%">商业套壳版 (HarkMulti 等)</th>
      <th width="40%">HarkFree (本开源净室重写项目)</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><b>商业模式</b></td>
      <td>免费试用 3 天，随后强制要求购买 <code>HK-XXXX</code> 激活码卡密，本质上是割韭菜。</td>
      <td><b>100% 永久免费、开源 MIT 许可</b>，无任何卡密、无广告、无推广。</td>
    </tr>
    <tr>
      <td><b>“无限Token”真相</b></td>
      <td><b>并非官方破解</b>。而是靠自动化脚本不断批量生成临时邮箱撞官方免费额度，平台一加验证码就彻底瘫痪。</td>
      <td><b>不搞黑灰产欺诈</b>。提供稳定可靠的多账号槽位管理，用户使用自己的正规账号，安全不封号。</td>
    </tr>
    <tr>
      <td><b>安装包体积</b></td>
      <td>~18.96MB（内含 37.6MB 解压的 Cloudflare 穿透工具与 PRoot 虚拟机，解压后占用 1GB+）。</td>
      <td><b>约 2MB 极简体积</b>。纯粹的原生 Android 构建，秒装秒开，不占手机空间。</td>
    </tr>
    <tr>
      <td><b>系统权限</b></td>
      <td>申请 Shizuku 系统提权、扫描手机全部 App、常驻反向穿透，存在明显的后门隐患。</td>
      <td><b>零多余敏感权限</b>。仅保留上网必需的 <code>INTERNET</code> 权限，安全透明。</td>
    </tr>
  </tbody>
</table>

<br>

<h2 id="architecture">技术架构 (Under the Hood)</h2>

```text
HarkFree App (MainActivity)
  ├── 账号槽位 1  ──>  [Process :slot0]  ──>  WebView (hark.com)  ──>  隔离 Cookie A
  ├── 账号槽位 2  ──>  [Process :slot1]  ──>  WebView (hark.com)  ──>  隔离 Cookie B
  ├── 账号槽位 3  ──>  [Process :slot2]  ──>  WebView (hark.com)  ──>  隔离 Cookie C
  └── 账号槽位 4  ──>  [Process :slot3]  ──>  WebView (hark.com)  ──>  隔离 Cookie D
                            │
                            ├──> HarkNativeBridge (本地 Java 桥)
                            │       ├── onExportSuccess ──> MediaStore 写入 /Download/HarkFree/*.md
                            │       └── onExportProgress ──> Android 原生 Toast 反馈
                            └──> 静态注入资产 (Assets)
                                    ├── wechat_style.css (微信绿经典气泡排版)
                                    └── export_engine.js (无损 Markdown 递归抓取)
```

<br>

<h2 id="get-started">快速开始与编译指南 (Get Started)</h2>

你可以选择使用 **Android Studio 图形界面** 或 **Windows 终端命令行** 随时构建属于你自己的 APK：

### 方式一：使用 Android Studio 一键编译（推荐）
1. 启动 **Android Studio**；
2. 选择 **Open**，直接打开本项目目录：`d:\4rchive\Code\anti-ads\hark_free`；
3. Android Studio 会自动索引本机已安装的 Android SDK 并完成 Gradle 配置；
4. 点击顶部菜单 **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**；
5. 构建完成后，直接点击右下角提示框中的 **locate**，即可获得编译出的安装包 `app-debug.apk`。

### 方式二：使用 Windows 终端命令行编译
在 PowerShell 中运行（命令不包含注释符号）：

```powershell
Set-Location -Path "d:\4rchive\Code\anti-ads\hark_free"
gradle assembleDebug
```

编译产物路径位于：
`d:\4rchive\Code\anti-ads\hark_free\app\build\outputs\apk\debug\app-debug.apk`

若手机开启了 USB 调试并已连接电脑，可通过 ADB 快速推送到手机：
```powershell
adb install -r "d:\4rchive\Code\anti-ads\hark_free\app\build\outputs\apk\debug\app-debug.apk"
```

<br>

<h2 id="security">安全审计与免责声明 (Security & Disclaimer)</h2>

- **隐私安全**：HarkFree 不架设任何远程后端服务器，所有的账号数据和登录凭据均安全保存在 Android 系统的本地 WebView 沙盒中，绝不上报、绝不泄露。
- **开源协议**：本项目基于宽松的 [MIT 许可证](LICENSE) 发布，任何人均可自由审查代码、修改和分发。
- **免责声明**：HarkFree 为开源社区独立重构作品，与 Hark 官方没有任何隶属关系。Hark 及其相关标识为其合法拥有者的知识产权。请在遵守平台服务条款的前提下使用。
