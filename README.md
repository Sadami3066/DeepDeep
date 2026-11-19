<div align="center">

# Super PI Agent

**运行在 Android 上的个人智能体**

<sub>长按电源，一句话开始工作。</sub>

[简体中文](README.md) · [English](README.en.md)

<a href="https://github.com/Sadami3066/DeepDeep/releases"><img src="https://img.shields.io/badge/下载-最新版本-111111?style=for-the-badge&logo=github&logoColor=white" alt="下载最新版本" /></a>

<img src="https://img.shields.io/badge/Android-10%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android 10+" />
<img src="https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin" />
<img src="https://img.shields.io/badge/Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
<img src="https://img.shields.io/github/v/release/Sadami3066/DeepDeep?style=flat-square&color=111111" alt="最新版本" />
<img src="https://img.shields.io/badge/License-Apache--2.0-blue?style=flat-square" alt="Apache-2.0" />

</div>

[概览](#概览) · [能力](#能力) · [快速开始](#快速开始) · [安装与配置](#安装与配置) · [边界](#边界) · [开发](#开发与构建) · [文档](#文档与反馈) · [贡献](#贡献) · [License](#license)

## 概览

Super PI Agent 是跑在 Android 手机上的个人智能体。长按电源键唤醒，用语音或文字说出需求，它会在本机把事情做完：读写文件、操作其他应用、运行 Linux 命令、连接外部服务。

它不依赖服务器，也不需要电脑常开。模型请求只发给你配置的服务商；对话、文件、凭据与插件数据都保存在本机。

Super PI Agent 包含一套完整的 Agent Harness：工具、技能、长期记忆、MCP 连接器与插件系统；内置轻量 Linux 环境，覆盖大部分桌面办公与代码任务；通过插件可以接入飞书、企业微信、中山大学校园服务、邮箱、地图、打车、酒店、火车票等外部能力。

项目开源，代码可审计；除模型服务商与用户配置的 MCP/插件外，没有数据上传。

安装包从 [GitHub Releases](https://github.com/Sadami3066/DeepDeep/releases) 获取。

## 能力

- **多形态交互**：长按电源唤醒，悬浮层语音输入与朗读；对话、附件、图片与文件进入同一条线程。
- **手机操作**：通过屏幕工具读取界面、点击、输入与滚动；虚拟屏幕让 Agent 在后台操作其他 App。
- **本机执行**：内置 Alpine/proot 用户空间，可运行 shell、Node、Python 与常用编译工具链；Root 档使用原生 root 环境。
- **帮我盯**：定时与后台任务持续检查状态，满足条件时执行下一步。
- **小程序**：用 HTML/CSS/JS 生成轻量小应用，可从桌面快捷方式直接打开。
- **插件生态**：安装签名 `.ddp` 插件，扩展工具、技能、页面与主题；市场提供插件、技能与远程 MCP。
- **模型自由**：支持多家模型服务商，API Key 或 OAuth 登录；模型与思考档按会话选择。
- **本地优先**：没有账号系统与自建后端，数据默认不离开设备。

## 快速开始

1. **下载安装包。** 从 [GitHub Releases](https://github.com/Sadami3066/DeepDeep/releases) 下载最新版本。
2. **安装并打开。** 系统要求 Android 10 及以上。首次启动会进入引导；建议跟着流程走，也可以跳过，之后在设置中逐项完成。
3. **连接模型。** 进入「设置 → 服务商」，选择服务商并填写 API Key，或使用 OAuth 登录。
4. **初始化 Linux。** 进入「设置 → 系统访问」的「Linux 环境」区完成 Alpine 初始化。
5. **选择系统访问。** 默认使用普通档。需要操作其他应用时，在「设置 → 系统访问」中开启无线调试或 Root；开启无线调试后，可以通过系统通知输入配对码。

## 安装与配置

### 安装

| 项目 | 说明 |
|---|---|
| 系统 | Android 10 及以上 |
| 安装包 | 从 [GitHub Releases](https://github.com/Sadami3066/DeepDeep/releases) 下载 APK |
| 账号 | 没有账号系统；模型 Key 仅保存在本机 |
| 升级 | 使用同一签名覆盖安装；更换签名需要先卸载，卸载会删除本机会话与凭据 |
| 网络 | 对话内容只发送给已配置的模型厂商；市场与更新检查访问 GitHub，部分网络环境需要代理 |

### 配置

| 配置项 | 位置 | 说明 |
|---|---|---|
| 模型服务商 | 设置 → 服务商 | 多家 Provider；API Key 或浏览器 OAuth |
| 系统访问 | 设置 → 系统访问 | 普通 / 无线调试 ADB / Root 三档 |
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
| **分发** | 通过 GitHub Releases 分发，未上架应用商店；源码已公开 |

## 开发与构建

<details>
<summary><b>模块、构建与验证</b></summary>

模块与依赖方向见 [架构说明](docs/ARCHITECTURE.md)。

开发环境：

- JDK 17；构建固定使用 Java 17（`jvmToolchain` 17）。
- Android SDK Platform 36、build-tools 36.0.0（约 5.6 GB）。
- 内存 ≥ 8 GB、空闲磁盘 ≥ 10 GB；冷缓存首次构建约 15–30 分钟。
- Android SDK 默认位置：macOS `~/Library/Android/sdk`，Linux 常见 `~/Android/Sdk`；可用 `ANDROID_HOME` 或 `ANDROID_SDK_ROOT` 指定。
- Windows 不原生支持：脚本依赖 bash 与 flock，请使用 WSL2、Linux 或 macOS。

```bash
./scripts/dev-setup.sh            # 首次：检查 JDK 与 Android SDK、按需安装 Platform 36/build-tools、构建 debug APK
./scripts/build.sh quick          # 日常编译
./scripts/build.sh test           # 定向单测
./scripts/build.sh check          # 完整验证（等价 check.sh --full）
./scripts/build.sh release        # 完整验证后构建 release APK
./scripts/check.sh                # 编译 + 单测 + lint + 模块边界 + APK
./scripts/check.sh --full         # 交付前：再加插件 SDK ABI 基线
./scripts/api-compat.sh           # 校验插件 SDK 的公开 ABI（--update 重建基线）
```

更完整的开发说明见 [开发指南](docs/DEVELOPER_GUIDE.md)。

</details>

## 文档与反馈

- [用户指南](docs/USER_GUIDE.md)：安装、系统访问、Linux、插件、小程序、远程连接与常见问题。
- [开发指南](docs/DEVELOPER_GUIDE.md)：环境、模块、构建、测试与代码约定。
- [插件开发](docs/PLUGIN_DEV.md)：插件契约、清单、签名、生命周期与 UI 扩展。
- [架构说明](docs/ARCHITECTURE.md)：模块、运行时、插件与更新模型。
- [产品原则](docs/PRODUCT.md)：产品与设计约束。
- [发布说明](docs/RELEASE.md)：签名、打包与分发流程。
- [变更记录](CHANGELOG.md)：按版本记录功能与修复。

## 贡献

欢迎提交 Issue 与 PR。提交前请阅读[贡献指南](CONTRIBUTING.md)。插件作者可以从[插件开发](docs/PLUGIN_DEV.md)与 [Super PI Agent 官方插件](https://github.com/Sadami3066/DeepDeep-Official-Plugins) 开始。安全相关问题请不要公开提交，直接联系作者。

## License

自有代码使用 [Apache-2.0](LICENSE)。第三方组件、字体与图标的许可信息见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

---

<sub>Built on [pi](https://pi.dev) · [DeepDeep-KotlinSDK](https://github.com/Sadami3066/DeepDeep-KotlinSDK) · Alpine Linux · Jetpack Compose</sub>
