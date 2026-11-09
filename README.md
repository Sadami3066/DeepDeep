<div align="center">

<img src="docs/media/mascot.webp" width="88" alt="DeepDeep" />

# DeepDeep

**Android 上的个人智能体**

<sub>对话、系统操作与 Linux 环境，运行在同一个应用内。</sub>

[简体中文](README.md) · [English](README.en.md)

<a href="https://github.com/Sadami3066/DeepDeep/releases"><img src="https://img.shields.io/badge/下载-最新版本-2EA043?style=for-the-badge&logo=github&logoColor=white" alt="下载最新版本" /></a>

<img src="https://img.shields.io/badge/Android-10%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android 10+" />
<img src="https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin" />
<img src="https://img.shields.io/badge/Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
<img src="https://img.shields.io/badge/1.0.0-6B7280?style=flat-square" alt="1.0.0" />
<img src="https://img.shields.io/badge/License-Apache--2.0-blue?style=flat-square" alt="Apache-2.0" />

<br />
<br />

<img src="docs/media/hero-zh.webp" width="100%" alt="DeepDeep：你的个人智能体，在手机端运行" />

</div>

[概览](#概览) · [预览](#预览) · [UI 展示](#ui-展示) · [快速开始](#快速开始) · [安装与配置](#安装与配置) · [功能](#功能) · [开发](#开发与构建) · [文档](#文档与反馈) · [贡献](#贡献) · [License](#license)

## 概览

DeepDeep 面向需要在手机上完成实际任务的用户。它将 Agent 运行时、Android 系统接口和 Alpine Linux 环境集成在同一个应用中，可以处理本地文件、检索网页、操作应用、执行 shell 命令，也可以按计划在后台运行任务。应用使用 Kotlin 与 Jetpack Compose 编写，大部分功能不需要 root。

适合需要在手机上处理文件、检索资料、操作应用、运行脚本，或把手机作为随身 Linux 环境的用户。

当前版本为 1.0.0，安装包发布在 [GitHub Releases](https://github.com/Sadami3066/DeepDeep/releases)，源码已公开。

## 预览

<table>
<tr>
<td width="33%" valign="top" align="center">

https://github.com/user-attachments/assets/bfeaac5e-da31-4790-a9e3-3f7e9a6b2018

<b>Agent 运行时</b><br />
<sub>查询空位并创建后台任务。</sub>
</td>
<td width="33%" valign="top" align="center">

https://github.com/user-attachments/assets/32019883-ca2d-40f6-b18f-6c941131cc64

<b>Android 系统集成</b><br />
<sub>在悬浮窗中操作其他应用。</sub>
</td>
<td width="33%" valign="top" align="center">

https://github.com/user-attachments/assets/9deb4845-514d-4c42-a197-0fa80f2cd9f4

<b>小程序</b><br />
<sub>现场生成打卡待办。</sub>
</td>
</tr>
<tr>
<td width="33%" valign="top" align="center">

https://github.com/user-attachments/assets/d387b523-4283-4269-9302-63f135e3541c

<b>万能扫一扫</b><br />
<sub>识别二维码并跳转到对应应用。</sub>
</td>
<td width="33%" valign="top" align="center">

https://github.com/user-attachments/assets/11c0d18c-192d-4e15-98bf-c6fb95498fae

<b>远程连接</b><br />
<sub>连接电脑上的 DSH。</sub>
</td>
<td width="33%" valign="top" align="center">

https://github.com/user-attachments/assets/a6e1d2c3-3d13-4ce4-afec-aaa0d1b5bf5b

<b>悬浮语音</b><br />
<sub>在桌面悬浮条中直接提问。</sub>
</td>
</tr>
</table>

## UI 展示

<p align="center"><img src="docs/media/shots/ui-showcase.png" width="100%" alt="DeepDeep 界面：主界面、抽屉、模型服务商、设置与个性化" /></p>

<sub>主界面、抽屉、模型服务商、设置与个性化。</sub>

## 快速开始

1. **下载安装包。** 从 [GitHub Releases](https://github.com/Sadami3066/DeepDeep/releases) 下载最新 `DeepDeep-1.0.0.apk`。
2. **安装并打开。** 系统要求 Android 10 及以上。首次启动会进入 6 屏引导；可以跳过，之后在设置中逐项完成。
3. **连接模型。** 进入「设置 → 服务商」，选择服务商并填写 API Key，或使用 OAuth 登录。模型与思考档按会话独立选择。
4. **初始化 Linux。** 进入「设置 → Linux 设置」完成 Alpine 初始化；之后可以在对话中直接使用 `bash`。
5. **选择权限。** 默认使用普通档。需要操作其他应用时，在「设置 → 权限」中开启无线调试或 Root。

如果使用 adb 安装：

```bash
adb install -r DeepDeep-1.0.0.apk
```

## 安装与配置

### 安装

| 项目 | 说明 |
|---|---|
| 系统 | Android 10 及以上 |
| 安装包 | 从 [GitHub Releases](https://github.com/Sadami3066/DeepDeep/releases) 下载 APK |
| 账号 | 没有账号系统；模型 Key 仅保存在本机 |
| 升级 | 使用同一签名覆盖安装；更换签名需要先卸载，卸载会删除本机会话与凭据 |
| 网络 | 对话内容只发送给已配置的模型厂商；市场与更新检查访问 GitHub |

首次启动的 6 屏引导依次覆盖欢迎、账号、模型、通知与悬浮窗、Linux 环境和权限。所有步骤都可以跳过，之后在设置中补齐。

### 配置

| 配置项 | 位置 | 说明 |
|---|---|---|
| 模型服务商 | 设置 → 服务商 | 支持 42 家 Provider；API Key 或浏览器 OAuth |
| 权限 | 设置 → 权限 | 普通 / 无线调试 / Root 三档；屏幕操作默认关闭 |
| Linux | 设置 → Linux 设置 | 初始化 Alpine；安装命令、Node、Python 与编译工具链 |
| Android 集成 | 设置 → Android 设置 | 悬浮窗、系统助手、通知与分享接收 |
| 远程连接 | 抽屉 → 远程连接 | 通过 [`dsh-mobile`](https://github.com/saya-ch/dsh-mobile) 配对电脑 |
| 插件 | 抽屉 → 插件 | 导入签名 `.ddp`；查看能力清单与运行状态 |
| 记忆 | 自动 | 会话结束时沉淀；可搜索，也可注入当前会话 |

## 功能

| 能力 | 说明 |
|---|---|
| **Agent 运行时** | 工具、MCP、技能、后台任务、定时任务与记忆在同一会话中运行；MCP 工具按需加载。 |
| **Android 系统集成** | 普通 / 无线调试 / Root 三档权限；虚拟屏、悬浮窗、系统助手、文件、分享与通知。 |
| **扩展与自动化** | 小程序、万能扫一扫、插件、市场、远程连接；后台任务与五段 cron。 |
| **模型与对话** | 42 家 Provider；Markdown、附件、语音、朗读、上下文占用与 `/compact`。 |

### Linux 环境

Alpine 通过 proot 运行，不依赖 root；初始化后可直接使用 `bash`，并按需安装 Node、Python、数据库客户端和 Android 编译工具链。工作文件默认位于 App 工作区，文件工具使用绝对路径。

## 设计

DeepDeep 将三类能力放在同一个应用中：Agent 运行时、Android 系统接口和 Linux 环境。Agent 循环、工具调度、会话存储与插件宿主使用 Kotlin 实现，App 本身不内嵌 Node 运行时；需要 Node 的插件在自己的 Linux 环境中安装。Linux 通过 proot 按需启动，不需要 root。

插件与 App 同进程、同权限，安装插件等同于信任其代码。应用不为插件提供独立沙箱；第三方工具源应优先使用 MCP。

## 边界

| 项目 | 说明 |
|---|---|
| **Linux 隔离** | Alpine 运行在 proot 用户空间，不是安全沙箱 |
| **插件权限** | 插件与 App 同进程、同 UID，没有沙箱 |
| **审批** | 只分普通、无线调试、Root 三档，不逐条确认命令 |
| **运行位置** | 任务在本机执行；关闭 App 后停止，目标续跑只在进程内有效 |
| **受保护页面** | 支付、密码等页面禁止截图，Agent 会把控制权交还给你 |
| **分发** | 1.0.0 通过 GitHub Releases 分发，未上架应用商店；源码已公开 |

## 开发与构建

<details>
<summary><b>模块、构建与验证</b></summary>

| 模块 | 职责 |
|---|---|
| `app/` | Compose UI、聊天、文件、设置、插件页、小程序容器与页面装配 |
| `runtime/` | Agent 运行时：会话、工具、记忆、任务、定时器、插件加载 |
| `runtime-api/` | 纯 JVM 契约，供 runtime 与宿主解耦 |
| `shell/` | proot 与 ADB/Root 特权通道 |
| `design/` | 主题、图标、通用组件与设计 token |
| `plugin-api/` | 插件契约与可发布 SDK |

需要 JDK 17 或更新版本、Android SDK Platform 36、build-tools 36.0.0。

```bash
./scripts/dev-setup.sh        # 首次：检查环境、写 local.properties、拉 SDK、出 APK
./scripts/check.sh            # 日常：编译 + 单测 + lint + 模块边界 + APK
./scripts/check.sh --full     # 交付前：再加插件 SDK ABI 基线
./scripts/check.sh --device   # 再加覆盖安装到手机并启动
```

`dev.pi` SDK 由 Gradle 任务从 pi2kotlin `v0.4.0` Release 下载并校验 sha256，不依赖本机 `~/.m2`。所有 Gradle 调用走 `./scripts/gradlew-locked`；本机并发构建可能 OOM。

更多内容见 [开发指南](docs/DEVELOPER_GUIDE.md) 与 [插件开发](docs/PLUGIN_DEV.md)。

</details>

## 文档与反馈

- [用户指南](docs/USER_GUIDE.md)：安装、权限、Linux、插件、小程序、远程连接与常见问题。
- [开发指南](docs/DEVELOPER_GUIDE.md)：环境、模块、构建、测试与代码约定。
- [插件开发](docs/PLUGIN_DEV.md)：插件契约、清单、签名、生命周期与 UI 扩展。
- [发布说明](docs/RELEASE.md)：签名、打包与分发流程。
- [变更记录](CHANGELOG.md)：按版本记录功能与修复。

## 贡献

欢迎提交 Issue 与 PR。提交前请阅读[贡献指南](CONTRIBUTING.zh-CN.md)。插件作者可以从[插件开发](docs/PLUGIN_DEV.md)与 [DeepDeep-Official-Plugins](https://github.com/Sadami3066/DeepDeep-Official-Plugins) 开始。安全相关问题请不要公开提交，直接联系作者。

## License

自有代码使用 [Apache-2.0](LICENSE)。第三方组件、字体与图标的许可信息见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

---

<sub>Built on [pi](https://pi.dev) · [pi2kotlin](https://github.com/Sadami3066/DeepDeep-KotlinSDK) · Alpine Linux · Jetpack Compose</sub>
