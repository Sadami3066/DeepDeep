<div align="center">

<img src="docs/media/mascot.webp" width="88" alt="DeepDeep" />

# DeepDeep

**Android 上的个人智能体**

<sub>深度求索，深知你心。</sub>

[简体中文](README.md) · [English](README.en.md)

<a href="https://github.com/Sadami3066/DeepDeep/releases"><img src="https://img.shields.io/badge/下载-最新版本-2EA043?style=for-the-badge&logo=github&logoColor=white" alt="下载最新版本" /></a>

<img src="https://img.shields.io/badge/Android-10%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android 10+" />
<img src="https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin" />
<img src="https://img.shields.io/badge/Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
<img src="https://img.shields.io/badge/1.0.2-6B7280?style=flat-square" alt="1.0.2" />
<img src="https://img.shields.io/badge/License-Apache--2.0-blue?style=flat-square" alt="Apache-2.0" />

<br />
<br />

<img src="docs/media/hero-zh.webp" width="100%" alt="DeepDeep：你的个人智能体，在手机端运行" />

</div>

[概览](#概览) · [功能预览](#功能预览) · [UI 展示](#ui-展示) · [快速开始](#快速开始) · [安装与配置](#安装与配置) · [开发](#开发与构建) · [文档](#文档与反馈) · [贡献](#贡献) · [License](#license)

## 概览

DeepDeep 对标所有手机厂商的内置智能助理，支持长按电源一键唤醒，浮层语音输入和输出。

同时配备完整的 Harness 工程，包含工具、技能、长期记忆、MCP 扩展和插件系统。它便捷地运行在手机上，无需服务器，无需电脑常开，随时随地都可以打开使用，只要你长按电源，一句话，它就能开始工作，把事情办好。

DeepDeep拥有内置的超轻量Linux环境，能够覆盖99%的电脑办公和代码任务，但本质运行在Android手机系统上，也能为你操作手机文件：找寻图片，清理垃圾，整理文件，编写代码，优化手机，甚至通过屏幕工具，开启虚拟屏幕操作你的APP。

除此之外，DeepDeep还拥有丰富的插件生态，能够通过mcp和cli连接外部服务，帮你处理远程任务：接入飞书，企业微信，高德地图，麦当劳MCP，Notion笔记，邮件等。为你的工作，生活，学习各个方面提高效率。

最后，DeepDeep秉持开源开放原则，代码足够安全透明，无任何外部网络服务（除了您向模型服务商的LLM网络请求外），全程无任何数据收集和上传行为。

使用 DeepDeep 时，请遵循 App 内的用户协议。

当前版本为 1.0.2，安装包发布在 [GitHub Releases](https://github.com/Sadami3066/DeepDeep/releases)，源码已公开。

## 功能预览

<table>
<tr>
<td width="33%" valign="top" align="center">

https://github.com/user-attachments/assets/a6e1d2c3-3d13-4ce4-afec-aaa0d1b5bf5b

<b>多形态交互</b><br />
<sub>长按电源唤醒，支持语音与文字交互。</sub>
</td>
<td width="33%" valign="top" align="center">

https://github.com/user-attachments/assets/bfeaac5e-da31-4790-a9e3-3f7e9a6b2018

<b>帮我盯</b><br />
<sub>监控预约系统，有空位直接订票。</sub>
</td>
<td width="33%" valign="top" align="center">

https://github.com/user-attachments/assets/32019883-ca2d-40f6-b18f-6c941131cc64

<b>虚拟屏幕操作 App</b><br />
<sub>后台不打扰，帮你看帮你做。</sub>
</td>
</tr>
<tr>
<td width="33%" valign="top" align="center">

https://github.com/user-attachments/assets/9deb4845-514d-4c42-a197-0fa80f2cd9f4

<b>小程序</b><br />
<sub>制作应用添加到桌面。</sub>
</td>
<td width="33%" valign="top" align="center">

https://github.com/user-attachments/assets/d387b523-4283-4269-9302-63f135e3541c

<b>统一扫码</b><br />
<sub>统一入口识别各类生态，可自定义策略。</sub>
</td>
<td width="33%" valign="top" align="center">

https://github.com/user-attachments/assets/11c0d18c-192d-4e15-98bf-c6fb95498fae

<b>远程连接</b><br />
<sub>连接电脑上的 DSH。</sub>
</td>
</tr>
</table>

## UI 展示

<p align="center"><img src="docs/media/shots/ui-showcase.png" width="100%" alt="DeepDeep 界面：主界面、抽屉、模型服务商、设置与个性化" /></p>

<sub>主界面、抽屉、模型服务商、设置与个性化。</sub>

## 快速开始

1. **下载安装包。** 从 [GitHub Releases](https://github.com/Sadami3066/DeepDeep/releases) 下载最新版本。
2. **安装并打开。** 系统要求 Android 10 及以上。首次启动会进入引导界面；建议跟着流程走，也可以跳过，之后在设置中逐项完成。
3. **连接模型。** 进入「设置 → 服务商」，选择服务商并填写 API Key，或使用 OAuth 登录。建议使用 DeepSeek 模型，或 GPT 订阅。
4. **初始化 Linux。** 进入「设置 → 系统访问」的「Linux 环境」区完成 Alpine 初始化；这一步建议认真完成，请根据提示完成基础配置。
5. **选择系统访问。** 默认使用普通档。需要操作其他应用时，在「设置 → 系统访问」中选择并跟随教程开启无线调试或 Root。开启无线调试时，你可以通过系统通知输入验证码，若系统通知无法使用，可以通过小窗化/分屏DeepDeep，在无线配对界面观察验证码，再在 DeepDeep 中输入。


## 安装与配置

### 安装

| 项目 | 说明 |
|---|---|
| 系统 | Android 10 及以上 |
| 安装包 | 从 [GitHub Releases](https://github.com/Sadami3066/DeepDeep/releases) 下载 APK |
| 账号 | 没有账号系统；模型 Key 仅保存在本机 |
| 升级 | 使用同一签名覆盖安装；更换签名需要先卸载，卸载会删除本机会话与凭据 |
| 网络 | 对话内容只发送给已配置的模型厂商；市场与更新检查访问 GitHub，可能需要VPN服务 |


### 配置

| 配置项 | 位置 | 说明 |
|---|---|---|
| 模型服务商 | 设置 → 服务商 | 支持 42 家 Provider；API Key 或浏览器 OAuth |
| 系统访问 | 设置 → 系统访问 | 普通 / 无线调试 ADB / Root 三档；档位决定 bash 与文件操作的身份 |
| Linux | 设置 → 系统访问 → Linux 环境 | 初始化 Alpine；安装命令、Node、Python 与编译工具链 |
| Android 集成 | 设置 → 安卓配置 | 悬浮窗、系统助手、通知与分享接收 |
| 远程连接 | 抽屉 → 远程连接 | 通过 [`dsh-mobile`](https://github.com/saya-ch/dsh-mobile) 配对电脑 |
| 插件 | 抽屉 → 插件 | 导入签名 `.ddp`；查看能力清单与运行状态 |
| 记忆 | 自动 | 会话结束时沉淀；可搜索，也可注入当前会话 |

## 边界

| 项目 | 说明 |
|---|---|
| **Linux 隔离** | 普通、无线调试档的 Alpine 运行在 proot 用户空间，不是安全沙箱；Root 档直接使用系统 root 环境 |
| **插件权限** | 插件与 App 同进程、同 UID，没有沙箱 |
| **审批** | 只分系统访问三档：普通、无线调试、Root，不逐条确认命令 |
| **运行位置** | 任务在本机执行；关闭 App 后停止，目标续跑只在进程内有效 |
| **受保护页面** | 支付、密码等页面禁止截图，Agent 会把控制权交还给你 |
| **分发** | 1.0.2 通过 GitHub Releases 分发，未上架应用商店；源码已公开 |

## 开发与构建

<details>
<summary><b>模块、构建与验证</b></summary>

| 模块 | 职责 |
|---|---|
| `app/` | Compose UI、聊天、文件、设置、插件页、小程序容器与页面装配 |
| `runtime/` | Agent 运行时：会话、工具、记忆、任务、定时器、插件加载 |
| `runtime-api/` | 纯 JVM 契约，供 runtime 与宿主解耦 |
| `shell/` | proot 与 ADB/Root 系统访问后端 |
| `design/` | 主题、图标、通用组件与设计 token |
| `plugin-api/` | 插件契约与可发布 SDK |

开发环境：

- JDK 17；构建固定使用 Java 17（`jvmToolchain` 17）。
- Android SDK Platform 36、build-tools 36.0.0（约 5.6 GB）。
- 内存 ≥ 8 GB、空闲磁盘 ≥ 10 GB；冷缓存首次构建约 15–30 分钟（含 JitPack 现场编译 SDK 与依赖下载）。
- Android SDK 默认位置：macOS `~/Library/Android/sdk`，Linux 常见 `~/Android/Sdk`。脚本按环境变量 → 已有 `local.properties` → 常见路径查找，可用 `ANDROID_HOME` 或 `ANDROID_SDK_ROOT` 指定。
- Windows 不原生支持：脚本依赖 bash 与 flock，请使用 WSL2、Linux 或 macOS。

```bash
./scripts/dev-setup.sh            # 首次：检查 JDK 与 Android SDK、按需安装 Platform 36/build-tools、构建 debug APK
./scripts/build.sh quick          # 日常编译
./scripts/build.sh test           # 定向单测
./scripts/build.sh check          # 完整验证（等价 check.sh --full）
./scripts/build.sh release        # 完整验证后构建 release APK
./scripts/check.sh                # 编译 + 单测 + lint + 模块边界 + APK
./scripts/check.sh --full         # 交付前：再加插件 SDK ABI 基线
./scripts/check.sh --device       # 再加覆盖安装到手机并启动
./scripts/plugin-e2e.sh           # 真机插件端到端
./scripts/plugin-bundles-e2e.sh   # 四个官方插件包回归（需先构建插件仓库）
./scripts/plugin-api-abi.sh       # 单独校验插件 SDK ABI 基线（--update 更新）
```

Agent SDK 经 JitPack 获取：`com.github.Sadami3066.DeepDeep-KotlinSDK:pi-coding-agent-android:v0.6.3`（版本即 `gradle/libs.versions.toml` 里的 `pi`）。首次构建可能等待 JitPack 现场编译 SDK（几分钟，也可能超时）；依赖缓存后可以离线构建。断网环境请先在联网环境完整构建一次。

真机脚本需要 `adb`；多设备在线时用 `ANDROID_SERIAL` 指定（未指定且多台在线会报错）。`plugin-e2e.sh` 默认使用 `../plugins`，`plugin-bundles-e2e.sh` 默认使用 `../DeepDeep-Official-Plugins`，可用 `PLUGINS_DIR` 覆盖；两者都需要 arm64 真机并已 root（`su -c` 可用或 `adb root` 成功）。`plugin-e2e.sh` 还需要插件仓库里的 `sample` 模块，DeepDeep-Plugins 目前只有 `template`，缺失时脚本会直接报错。`plugin-api-abi.sh` 需要 `unzip`、`javap`（`jar` 可选，缺省时用 `unzip` 回退）。

x86_64 模拟器可以完成单测、编译、安装启动和绝大多数 UI 开发；Linux 环境、插件 e2e、屏幕操作与 Root 档系统访问必须用 arm64 真机。

所有 Gradle 调用走 `./scripts/gradlew-locked`；本机并发构建可能 OOM。

更多内容见 [开发指南](docs/DEVELOPER_GUIDE.md) 与 [插件开发](docs/PLUGIN_DEV.md)。

</details>

## 文档与反馈

- [用户指南](docs/USER_GUIDE.md)：安装、系统访问、Linux、插件、小程序、远程连接与常见问题。
- [开发指南](docs/DEVELOPER_GUIDE.md)：环境、模块、构建、测试与代码约定。
- [插件开发](docs/PLUGIN_DEV.md)：插件契约、清单、签名、生命周期与 UI 扩展。
- [发布说明](docs/RELEASE.md)：签名、打包与分发流程。
- [变更记录](CHANGELOG.md)：按版本记录功能与修复。

## 贡献

欢迎提交 Issue 与 PR。提交前请阅读[贡献指南](CONTRIBUTING.zh-CN.md)。插件作者可以从[插件开发](docs/PLUGIN_DEV.md)与 [DeepDeep-Official-Plugins](https://github.com/Sadami3066/DeepDeep-Official-Plugins) 开始。安全相关问题请不要公开提交，直接联系作者。

## License

自有代码使用 [Apache-2.0](LICENSE)。第三方组件、字体与图标的许可信息见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

---

<sub>Built on [pi](https://pi.dev) · [DeepDeep-KotlinSDK](https://github.com/Sadami3066/DeepDeep-KotlinSDK) · Alpine Linux · Jetpack Compose</sub>
