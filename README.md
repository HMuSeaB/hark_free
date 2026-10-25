<h1 align="center">HarkFree</h1>

<p align="center">
  <b>A powerful, open-source, clean-room reimplementation of the multi-account container & geek workstation for Hark AI.</b><br>
  Multi-process slot isolation, full conversation Markdown exporter, WeChat-style bubbles, multi-distro Linux containers<br>
  (Alpine / Debian 13 / Ubuntu 24.04), a zero-cable Web transfer hub with public Cloudflare bridge for Agents. 100% free, transparent, and yours.
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
      <h3>💻 手机里的多发行版 Linux</h3>
      基于开源 PRoot 引擎,免 Root 一键安装 <b>Alpine (~3.5MB) / Debian 13 / Ubuntu 24.04</b> 三种发行版,阿里云→清华→官方镜像三级回退,直接在手机上跑 Python、Git 与 Shell 脚本。
    </td>
    <td width="25%" valign="top">
      <h3>⚡ 免数据线 Web 互传 + 公网桥接</h3>
      手机后台启动极简 HTTP 服务(<code>:8080</code>,随机令牌保护),电脑浏览器直连互传;自动建立 <b>Cloudflare 公网桥接</b>,Agent 与远端协作者从任何网络安全接入。
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
        <li><b>电脑浏览器直连</b>：电脑或平板打开手机 IP（例如 <code>http://192.168.1.100:8080/?t=令牌</code>），无需微信传输助手或数据线；</li>
        <li><b>随机令牌保护</b>：每次启动服务生成随机访问令牌,同网段陌生人无法访问你的文件与 Shell;</li>
        <li><b>公网桥接 (Agent Bridge)</b>：启动互传时自动建立 Cloudflare 快速隧道,把控制台安全发布到公网——Agent 和远程协作者从任何网络接入,令牌保护全程生效;</li>
        <li><b>拖拽极速上传</b>：直接将电脑文件拖入网页,自动存入手机应用存储;支持在线预览、下载或删除;</li>
        <li><b>网页调试终端</b>：支持直接在电脑网页上向手机下发 Shell 调试命令(完整读取不截断)。</li>
      </ul>
    </td>
    <td width="50%" valign="top">
      <h3>🐧 手机端免 Root 多发行版 Linux 容器 (PRoot)</h3>
      对标商业版 v8.0 的多发行版能力,且全程免 Root:
      <ul>
        <li><b>三种发行版任选</b>:Alpine(3.5MB 极小极快)/ Debian 13(90MB 最稳软件最全)/ Ubuntu 24.04;</li>
        <li><b>国内镜像三级回退</b>:阿里云 → 清华 → 官方源,目录动态解析最新版,告别下载卡死;</li>
        <li><b>Java 原生 tar/gzip/xz 解包</b>:不依赖系统 tar,支持长文件名/符号链接,rootfs 解包路径强制校验;</li>
        <li><b>真正的 Linux 环境</b>:支持 <code>apk</code>/<code>apt</code> 双包管理,AI 写的 Python 爬虫和工具直接全速运行。</li>
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
      <h3>🌐 公网桥接与静态站点托管</h3>
      让手机成为你的个人微型发布节点:
      <ul>
        <li><b>Sites 静态托管</b>：将 AI 生成的 HTML/JS/CSS 项目放置于站点目录,手机直接提供本地 HTTP 预览;</li>
        <li><b>内置 Cloudflare 快速隧道</b>：v1.0.1 起内置 cloudflared(来源 Termux 构建,可审计),启动互传即自动发布公网地址,无需注册、无需填 Token;高级用户也可填入自己的 Cloudflare Zero Trust Token 使用固定隧道;</li>
        <li><b>令牌全程护航</b>：公网地址同样强制令牌校验,路径穿越/越权方法一律拒绝。</li>
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
      <td>解压庞大的完整 Ubuntu,占用手机 1GB+ 存储,下载容易卡死。</td>
      <td><b>三种发行版任选 + 国内镜像三级回退 + Java 原生解包</b>,Alpine 仅 3.5MB 秒级装好,Debian/Ubuntu 走 LXC/云镜像同样不卡死。</td>
    </tr>
    <tr>
      <td><b>局域网互传</b></td>
      <td>绑定作者的闭源通信服务，部分通道受限。</td>
      <td><b>纯原生轻量多线程 HTTP Server</b>，代码透明可见，无任何外发遥测。</td>
    </tr>
    <tr>
      <td><b>公网穿透</b></td>
      <td>固定连接作者的某些后端,存在双向穿透后门隐患。</td>
      <td><b>内置开源 cloudflared(Termux 构建,可审计)</b>直连 Cloudflare 官方边缘,令牌保护,无作者后端;高级用户可换自己的 Zero Trust 隧道。</td>
    </tr>
    <tr>
      <td><b>系统权限</b></td>
      <td>申请 Shizuku 提权、录音相机、查询全部应用、INTERACT_ACROSS_USER。</td>
      <td><b>基础网络 + 前台服务 + 通知</b>,仅此而已;互传与隧道受随机令牌保护,无录音相机,无全域应用扫描。</td>
    </tr>
  </tbody>
</table>

<br>

<h2 id="get-started">快速编译与运行 (Get Started)</h2>

你可以选择使用 **Android Studio 图形界面** 或 **Windows 终端命令行** 随时构建属于你自己的 APK：

### 方式一：使用 Android Studio 一键编译（推荐）
1. 打开 **Android Studio**；
2. 选择 **Open**，直接打开目录：`D:\4rchive\Code\hark_free`；
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

Set-Location -Path "D:\4rchive\Code\hark_free"
gradle assembleDebug
```

编译产物路径位于：
`D:\4rchive\Code\hark_free\app\build\outputs\apk\debug\app-debug.apk`

若手机开启了 USB 调试并已连接电脑，可通过 ADB 快速推送到手机：
```powershell
adb install -r "D:\4rchive\Code\hark_free\app\build\outputs\apk\debug\app-debug.apk"
```

<br>

<h2 id="changelog">更新日志与路线图 (Changelog & Roadmap)</h2>

### v1.0.1 (2026-10-25)
- **[安全] 修复存储写入全灭**：互传/站点/导出迁移到应用专属目录,API 26~34 全版本免权限;
- **[崩溃] 修复槽位串号/闪退**:每槽位进程独立 WebView 数据目录后缀(API 28+);
- **[安全] 本地服务器加固**:随机令牌鉴权、路径穿越防护、方法白名单、Shell 输入完整读取、线程池上限;
- **[修复] 额度看板跨进程同步**:主界面不再永远显示 0%;
- **[新增] Linux 多发行版**:Alpine / Debian 13 / Ubuntu 24.04,国内镜像三级回退,Java 原生解包;
- **[新增] 公网桥接**:内置 cloudflared,互传启动自动发布公网隧道(令牌保护);
- **[新增] 前台服务保活 + 电池优化豁免引导**;
- **[优化] 登录页自动隐藏官方推广横幅**。

### Roadmap
- [ ] SSH 服务器管理页(多服务器+密钥)
- [ ] Shizuku 免 root 提权(可选,解锁更多极客能力)
- [ ] 导出引擎契约实测固化(基于真实登录态抓包)
- [ ] 槽位自定义 URL(把容器装到任何服务,含自建后端)
- [ ] release 签名与版本自动管理

<br>

<h2 id="security">安全审计与开源协议 (Security & License)</h2>

- **代码透明**：全套工程纯 Java 原生编写，PRoot 动态库均为开源社区标准构建，无任何混淆黑盒。
- **开源协议**：本项目基于宽松的 [MIT 许可证](LICENSE) 发布。
- **免责声明**：HarkFree 为开源社区独立极客项目，与 Hark 官方没有任何隶属关系。Hark 及其相关标识为其合法拥有者的知识产权。
