<h1 align="center">HarkFree</h1>

<p align="center">
  <b>A powerful, open-source, clean-room reimplementation of the multi-account container & geek workstation for Hark AI.</b><br>
  Multi-process slot isolation, full conversation Markdown exporter, WeChat-style bubbles, an embedded Alpine Linux container (PRoot),<br>
  and a zero-cable local Web transfer hub. 100% free, transparent, and yours.
</p>

<p align="center">
  <img alt="Platform: Android" src="https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-3DDC84?style=flat-square&logo=android&logoColor=white">
  <img alt="Language: 100% Java & Native Android" src="https://img.shields.io/badge/100%25-Native%20Android-07C160?style=flat-square">
  <img alt="Linux Inside" src="https://img.shields.io/badge/Linux-PRoot%20Alpine-0D597F?style=flat-square&logo=alpinelinux&logoColor=white">
  <img alt="License: MIT" src="https://img.shields.io/badge/License-MIT-blue?style=flat-square">
  <img alt="Zero Bloat" src="https://img.shields.io/badge/Architecture-Clean--room%20Geek-brightgreen?style=flat-square">
</p>

<p align="center">
  <a href="#features">功能矩阵</a> ·
  <a href="#why-clean-room">净室重构对照</a> ·
  <a href="#web-console">局域网互传控台</a> ·
  <a href="#linux-container">内置 Linux 终端</a> ·
  <a href="#token-tracker">Token 额度监控</a> ·
  <a href="#chat-export">会话 Markdown 导出</a> ·
  <a href="#get-started">编译与安装</a> ·
  <a href="#security">安全审计</a>
</p>

<br>

<table>
  <tr>
    <td width="25%" valign="top">
      <h3>🎛️ 物理进程级多开</h3>
      4 个完全独立的物理子进程（<code>:slot0</code> 到 <code>:slot3</code>），Cookie、LocalStorage 与内存沙盒物理隔离，彻底杜绝串号。
    </td>
    <td width="25%" valign="top">
      <h3>💻 手机里的 Alpine Linux</h3>
      基于开源 PRoot 引擎，免 Root 一键拉取清华大学源极简 Alpine Linux (~3.5MB)，直接在手机上跑 Python、Git 与 Shell 脚本。
    </td>
    <td width="25%" valign="top">
      <h3>⚡ 免数据线 Web 互传</h3>
      手机后台启动极简 HTTP 服务（<code>:8080</code>），电脑浏览器直接打开，拖拽极速互传文件、查看手机存储、网页执行 Shell。
    </td>
    <td width="25%" valign="top">
      <h3>📊 官方 Token 实时监控</h3>
      直连官方接口提取每日限额（<code>dailyLimit</code>）、已用量与重置倒计时，配额百分比一目了然；支持一键无损导出为 Markdown。
    </td>
  </tr>
</table>

<br>

<h2 id="features">核心功能深度拆解 (Features)</h2>

<table>
  <tr>
    <td width="50%" valign="top">
      <h3>⚡ 电脑-手机免数据线互传 (Local Web Console)</h3>
      在手机主界面点击<b>「启动互传」</b>，手机即刻成为微型文件服务器：
      <ul>
        <li><b>电脑浏览器直连</b>：电脑或平板打开手机 IP（例如 <code>http://192.168.1.100:8080</code>），无需微信传输助手或数据线；</li>
        <li><b>拖拽极速上传</b>：直接将电脑文件拖入网页，自动存入手机 <code>Download/HarkFree/</code>；</li>
        <li><b>在线文件管理</b>：支持在电脑端一键预览、下载或删除手机导出的 Markdown 会话与附件；</li>
        <li><b>网页调试终端</b>：支持直接在电脑网页上向手机下发 Shell 调试命令。</li>
      </ul>
    </td>
    <td width="50%" valign="top">
      <h3>🐧 手机端免 Root Alpine Linux 容器 (PRoot)</h3>
      告别原版动辄 1GB+ 的臃肿虚拟机镜像，采用极轻量化方案：
      <ul>
        <li><b>极小体积</b>：集成 6 大原生 PRoot 动态库（仅 ~390KB），配合清华镜像源 3.5MB 的 Alpine Rootfs；</li>
        <li><b>免 Root 执行</b>：利用 <code>ptrace</code> 机制拦截系统调用，无需解锁手机 BL 或 Root；</li>
        <li><b>真正的 Linux 环境</b>：支持 <code>apk add python3 py3-pip git curl</code>，AI 写的 Python 爬虫和工具可以直接在手机里全速运行；</li>
        <li><b>内置终端 (TermActivity)</b>：自带绿色等宽字体控制台与快捷命令（<code>uname</code>, <code>apk update</code>, <code>df</code>）。</li>
      </ul>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <h3>📊 官方 Token 额度实时监控看板 (Token Tracker)</h3>
      绝不使用虚假“无限Token”忽悠用户，真实呈现官方配额：
      <ul>
        <li>进入各账号槽位时，自动拉取 Hark 官方额度元数据；</li>
        <li>主界面直观展示各槽位的用量百分比进度条（例如 <code>今日已用: 45% (4500/10000)</code>）；</li>
        <li>精确显示官方<b>重置时间倒计时</b>（<code>dailyResetsAt</code>），各账号额度何时回血一眼看清。</li>
      </ul>
    </td>
    <td width="50%" valign="top">
      <h3>🌐 本地静态站点托管与 Cloudflare 穿透</h3>
      让手机成为你的个人微型发布节点：
      <ul>
        <li><b>Sites 静态托管</b>：将 AI 生成的 HTML/JS/CSS 项目放置于 <code>HarkFree/sites/</code>，手机可直接提供本地 HTTP 预览；</li>
        <li><b>Cloudflare 穿透支持</b>：提供 <code>TunnelManager</code> 框架，用户可填入自己的 Cloudflare Zero Trust Token，一键将手机本地的站点或控制台发布到公网，安全受控、绝无后门。</li>
      </ul>
    </td>
  </tr>
</table>

<br>

<h2 id="why-clean-room">净室重构深度对照 (Clean-room Comparison)</h2>

<table>
  <thead>
    <tr>
      <th width="20%">功能模块</th>
      <th width="40%">商业套壳版 (HarkMulti)</th>
      <th width="40%">HarkFree (开源全功能极客版)</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><b>商业模式</b></td>
      <td>试用 3 天，后续强制购买 <code>HK-XXXX</code> 激活码卡密，本质是割韭菜。</td>
      <td><b>100% 永久免费、MIT 协议完全开源</b>，零激活码、零卡密。</td>
    </tr>
    <tr>
      <td><b>Linux 容器</b></td>
      <td>解压庞大的完整 Ubuntu，占用手机 1GB+ 存储，下载容易卡死。</td>
      <td><b>采用极简 Alpine Linux (~3.5MB)</b>，清华大学开源镜像源高速直连，秒级下载解压。</td>
    </tr>
    <tr>
      <td><b>局域网互传</b></td>
      <td>绑定作者的闭源通信服务，部分通道受限。</td>
      <td><b>纯原生轻量多线程 HTTP Server</b>，代码透明可见，无任何外发遥测。</td>
    </tr>
    <tr>
      <td><b>公网穿透</b></td>
      <td>打包 37.6MB 的 <code>libcloudflared.so</code> 并固定连接作者的某些后端，存在双向穿透后门隐患。</td>
      <td><b>开放式透明配置</b>：用户自主掌控隧道配置，不设硬编码远程接入后门。</td>
    </tr>
    <tr>
      <td><b>系统权限</b></td>
      <td>申请 Shizuku 提权、录音相机、查询全部应用。</td>
      <td><b>普通应用沙盒运行</b>，仅需基础网络权限，隐私无忧。</td>
    </tr>
  </tbody>
</table>

<br>

<h2 id="get-started">快速编译与运行 (Get Started)</h2>

你可以选择使用 **Android Studio 图形界面** 或 **Windows 终端命令行** 随时构建属于你自己的 APK：

### 方式一：使用 Android Studio 一键编译（推荐）
1. 打开 **Android Studio**；
2. 选择 **Open**，直接打开目录：`d:\4rchive\Code\anti-ads\hark_free`；
3. 等待 Gradle 完成依赖同步（自动适配本地 Android SDK API 34）；
4. 点击顶部菜单 **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**；
5. 构建完成后，直接点击右下角气泡里的 **locate**，即可获得编译出的安装包 `app-debug.apk`。

### 方式二：使用 Windows 终端命令行编译
在 PowerShell 中运行：

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

<h2 id="security">安全审计与开源协议 (Security & License)</h2>

- **代码透明**：全套工程纯 Java 原生编写，PRoot 动态库均为开源社区标准构建，无任何混淆黑盒。
- **开源协议**：本项目基于宽松的 [MIT 许可证](LICENSE) 发布。
- **免责声明**：HarkFree 为开源社区独立极客项目，与 Hark 官方没有任何隶属关系。Hark 及其相关标识为其合法拥有者的知识产权。
