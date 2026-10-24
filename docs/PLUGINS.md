# 插件

状态：官方插件框架与首个飞书插件已实现；飞书授权需要用户在浏览器登录飞书账号。

## 模型

插件是 App 内置的官方能力包，按需下载，不默认安装。插件页只管三件事：安装记录、插件自己实现的生命周期、界面上的通用展示。

插件框架不包含 Linux、CLI、登录、鉴权等具体概念。每个插件实现 `OfficialPlugin`：

```kotlin
interface OfficialPlugin {
    val id: String
    val nameRes: Int
    val summaryRes: Int
    val version: String
    val sizeLabelRes: Int

    suspend fun availability(): PluginAvailability
    suspend fun install(progress: PluginProgress)
    suspend fun uninstall()
    suspend fun recover()
    suspend fun status(): PluginStatus
    suspend fun setup(ui: PluginUi)
    fun actions(status: PluginStatus): List<PluginAction>
    suspend fun runAction(actionId: String, ui: PluginUi)
    suspend fun detailRows(status: PluginStatus): List<PluginDetailRow>
    fun skills(): List<PluginSkill>
    fun promptBody(status: PluginStatus): String?
}
```

- `availability()`：插件自己判断当前能否安装/使用（例如飞书要求 Linux 环境）。
- `status()`：插件自己定义「就绪」的含义。飞书在这里执行 `lark-cli auth status --json` 并映射成 `Ready` / `NotReady` / `Failed`；框架不认识「登录」。
- `setup(ui)`：插件自己的准备流程。飞书通过 `ui.stage(...)` 展示建应用/授权链接，框架只渲染通用 sheet。
- `actions()` / `runAction()`：插件自己的操作行（连接、重新授权、退出登录、重新检测）。
- `skills()` / `promptBody()`：插件贡献的技能与系统提示内容。

框架（`PluginManager`）负责：安装记录、互斥、安装/卸载/更新的通用流程、技能同步、系统提示拼接、状态刷新、进度通知、`plugin_setup` 工具入口。它不引用 shell 模块，也不引用任何具体插件代码。

## 文件

| 路径 | 内容 |
|---|---|
| `files/plugins/plugins.json` | 安装记录，原子写；损坏时保留原文件并报错 |
| `files/agent/skills/<skill-id>/` | 插件贡献的技能；`.deepdeep-plugin` 标记归属，框架只删除自己写入的目录 |
| `cacheDir/plugin-staging/<plugin-id>/` | 下载/解压暂存；启动和安装结束时清理 |
| `/usr/local/bin/lark-cli` | 飞书 CLI 二进制（guest 内） |
| `/root/.lark-cli` | 飞书 CLI 应用配置（guest 内） |
| `/root/.local/share/lark-cli` | 飞书 CLI 凭证（guest 内，Linux 下为 AES-GCM 加密文件） |

安装记录 schema：

```json
{"schemaVersion":1,"plugins":[{"id":"feishu","version":"1.0.98","installedAt":1729750000000,"skillIds":["deepdeep-feishu"]}]}
```

## 界面

- 抽屉「插件」→ 插件列表。官方插件全部列出；未安装显示「下载」，已安装显示删除按钮与详情入口。
- 详情页：状态、版本、大小、插件自带的详情行（飞书：CLI 版本、已授权权限数量、飞书应用状态）、插件技能。
- 操作行来自插件本身；框架另外提供下载/更新/重新安装/删除插件。
- 安装或插件的准备流程进行中时，列表与详情页的操作会禁用；进度同时显示在行内与通知里。
- 全局 setup sheet：通用确认安装、进度、授权链接、「打开浏览器」、等待提示、取消。
- 卸载会删除二进制、插件配置、CLI 凭证、插件技能与安装记录；重新安装从零开始。

## 飞书插件

首个官方插件，安装的是飞书官方 CLI（`lark-cli`）。

### 安装

- 目标：Linux arm64 guest；要求 Linux 环境已安装并启用。
- 下载源：npmmirror 的 `lark-cli-1.0.98-linux-arm64.tar.gz`，GitHub release 兜底。
- 校验：内置 SHA-256；解压后先跑 `lark-cli --version` 校验版本，再原子复制到 `/usr/local/bin/lark-cli`。
- 版本随 App 发布固定；升级 App 后插件页显示更新。
- 该二进制不进入「运行环境」的包列表，只由插件页管理。

### 状态

```bash
LARKSUITE_CLI_NO_UPDATE_NOTIFIER=1 LARKSUITE_CLI_NO_SKILLS_NOTIFIER=1 lark-cli auth status --json
```

解析 `appId` 与 `identities.user.{available,userName,scope}`，映射为「已连接 · 账号」或「未连接」。

### 连接

1. 未建应用时运行 `lark-cli config init --new --brand feishu`，从输出提取链接并展示；命令会阻塞到用户在浏览器完成。
2. 运行 `lark-cli auth login --domain im,calendar,drive,docs,mail,task,contact --no-wait --json`，拿 `verification_url` 与 `device_code`。
3. 展示授权链接，随后运行 `lark-cli auth login --device-code <code> --json` 轮询完成。
4. 再探测一次状态，返回结果。

取消会终止底层 PRoot 进程；退出登录只运行 `lark-cli auth logout`。

### 技能与提示词

- 技能 `deepdeep-feishu`：只写命令用法与安全规则（`--as user`、写操作前确认、`--dry-run`、用 `lark-cli skills read` 查文档），不预设任何任务或总结流程。
- 系统提示 `official_plugins`：每轮按插件当前状态生成；未连接时提示模型调用 `plugin_setup`，不要自己跑交互式登录命令。

## Agent 与显示的同步

- 单一状态源：`PluginManager.state: StateFlow<PluginsUiState>`，列表页、详情页、setup sheet 都只订阅它。
- Agent 调用 `plugin_setup` 时走同一个 `PluginManager.setup()`，sheet 是状态的视图；用户取消会取消协程并杀掉 PRoot 进程，工具返回取消结果。
- Agent 直接用 bash 操作 CLI 是允许的；插件页进入、App 恢复、一轮运行结束会重新探测，界面与下一轮提示词随之更新。
- 状态不缓存到安装记录或 UI：是否连接永远来自插件自己的 `status()`。

## 失败处理

| 场景 | 处理 |
|---|---|
| Linux 未安装/未启用/非 arm64 | 插件返回不可用原因，禁用下载与连接 |
| Linux 被重置 | 记录仍在、探测失败 → `Failed`，可重新安装 |
| 下载失败或超时 | 依次换镜像；清理暂存，不写记录 |
| SHA-256 不匹配 | 硬失败，不落盘 |
| 解压/版本不符 | 回滚二进制，不写记录 |
| 建应用/授权超时或拒绝 | 结束 setup，显示插件返回的错误 |
| 用户取消 | 取消协程 → 杀 PRoot 进程，工具返回 cancelled |
| 安装记录损坏 | 保留原文件，页面显示错误 |
| 卸载清理失败 | 保留记录与错误，允许重试 |

## 新增插件

1. 在 `app/.../plugin/<name>/` 实现 `OfficialPlugin`，自己决定 availability / install / status / setup / actions / skills / promptBody。
2. 在 `AppGraph` 的 `plugins = listOf(...)` 里加入实例。
3. UI 自动出现列表行、详情页和通用 setup sheet；如果插件需要自绘配置页，在 `PluginDetailScreen` 按 id 挂 composable（v1 未使用）。

不涉及 Linux 的插件可以这样实现：`availability()` 恒为可用，`install()` 为空，`status()` 返回自己的就绪状态，`setup()` 用 `ui.stage(...)` 展示引导或打开 App 内页面——框架不需要改动。

## 限制

- v1 只支持官方内置插件，不支持第三方插件源或远程插件市场。
- 飞书授权需要用户在浏览器登录飞书账号；没有登录态时只会停在登录页。
- 飞书写操作的安全约束是提示词层面的（写前确认、优先 `--dry-run`），没有 scope 级强制。
- 不预设定时任务、每日总结或总结模板；需要时由用户用现有 `schedule_*` 工具与 prompt 自行组合。
