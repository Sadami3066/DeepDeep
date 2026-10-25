# 插件

状态：官方插件框架与飞书 CLI、网络搜索、GUI 操作、安排、小米云服务、企业微信插件已实现；飞书与企业微信授权需要用户登录对应账号。

## 模型

插件是 App 内置的官方能力包，按需下载，不默认安装。插件页只管：安装记录、启用状态、插件自己实现的生命周期、界面上的通用展示。

插件框架不包含 Linux、CLI、登录、鉴权等具体概念。每个插件实现 `OfficialPlugin`：

```kotlin
interface OfficialPlugin {
    val id: String
    val icon: DdIcon
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
    fun tools(): List<CodingTool> = emptyList()
    fun promptBody(status: PluginStatus): String?
}
```

- `availability()`：插件自己判断当前能否安装/使用（例如飞书要求 Linux 环境）。
- `status()`：插件自己定义「就绪」的含义。飞书在这里执行 `lark-cli auth status --json` 并映射成 `Ready` / `NotReady` / `Failed`；框架不认识「登录」。
- `setup(ui)`：插件自己的准备流程。飞书通过 `ui.stage(...)` 展示建应用/授权链接，框架只渲染通用 sheet。
- `actions()` / `runAction()`：插件自己的操作行（连接、重新授权、应用权限、退出登录）。
- `skills()` / `tools()` / `promptBody()`：插件贡献的技能、工具与系统提示内容。框架只收集已安装且已启用插件的贡献。

框架（`PluginManager`）负责：安装记录、启用状态、互斥、安装/卸载/更新的通用流程、技能同步、工具注入、系统提示拼接、状态刷新、进度通知、`plugin_setup` 工具入口。它不引用 shell 模块，也不引用任何具体插件代码。

### 启用与贡献注入

- `plugins.json` 为每个插件保存 `enabled`；默认开启。
- 关闭插件后：不再探测它的 `status()`，不再注入它的 `promptBody()`、skills、tools；技能从生效目录移除，仓库副本保留；重新开启时恢复。
- tools 在会话创建时收集；安装/卸载/启用/关闭会在没有运行中的会话时调用 `host.reconfigure()`，让新会话拿到最新贡献。
- 已关闭插件的 CLI 二进制仍留在 guest 内，但没有任何技能或提示引导模型使用它。

## 文件

| 路径 | 内容 |
|---|---|
| `files/plugins/plugins.json` | 安装记录与启用状态，原子写；损坏时保留原文件并报错 |
| `files/skills/<skill-id>/` | 插件技能的仓库副本；技能页可见，`.deepdeep-plugin` 标记归属 |
| `files/agent/skills/<skill-id>/` | 插件技能的生效副本；仅插件启用且技能开启时存在 |
| `cacheDir/plugin-staging/<plugin-id>/` | 下载/解压暂存；启动和安装结束时清理 |
| `/usr/local/bin/lark-cli` | 飞书 CLI 二进制（guest 内） |
| `/root/.lark-cli` | 飞书 CLI 应用配置（guest 内） |
| `/root/.local/share/lark-cli` | 飞书 CLI 凭证（guest 内，Linux 下为 AES-GCM 加密文件） |

安装记录 schema：

```json
{"schemaVersion":2,"plugins":[{"id":"feishu","version":"1.0.98","installedAt":1729750000000,"skillIds":["deepdeep-feishu"],"enabled":true}]}
```

旧版 schemaVersion 1 的安装记录仍可读取，缺少 `enabled` 时视为开启。

## 界面

- 抽屉「插件」→ 插件列表。官方插件全部列出；每行左侧是插件图标，右侧是启用开关（未安装时显示「下载」）；已安装可点进详情。
- 标题右侧是刷新图标；卡片内不再提供删除，删除入口在详情页。
- 插件图标来自 `OfficialPlugin.icon`：飞书使用内置的飞书 logo（`tintIcon=false` 保留品牌色），网络搜索使用 `DdIcons.Globe`（随主题 tint）。
- 详情页：状态、版本、大小、插件自带的详情行、插件技能；飞书详情还包含 CLI 版本、已授权权限数量、飞书应用状态、缺失权限（如果有）。
- 操作行来自插件本身；框架另外提供下载/更新/重新安装/删除插件。
- 安装或插件的准备流程进行中时，列表与详情页的操作会禁用；进度同时显示在行内与通知里。
- 全局 setup sheet：通用确认安装、进度、授权链接、「打开浏览器」、等待提示、取消。授权完成后 sheet 自动关闭；App 会尝试回到前台。
- 卸载会删除二进制、插件配置、CLI 凭证、仓库/生效技能与安装记录；重新安装从零开始。


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

解析 `appId` 与 `identities.user.{available,userName,scope}`，映射为「已连接 · 账号」或「未连接」。授权过程中被服务端裁掉的 scope 会记录为缺失权限，状态显示「缺少 N 项权限」，详情页列出具体项。

### 连接

1. 未建应用时运行 `lark-cli config init --new --brand feishu`，从输出提取链接并展示；命令会阻塞到用户在浏览器完成。
2. 运行 `lark-cli auth login --domain all --no-wait --json`，拿 `verification_url` 与 `device_code`。
3. 展示授权链接，随后运行 `lark-cli auth login --device-code <code> --json` 轮询完成。
4. 轮询返回 0 或部分授权（带 `authorization_complete` 账号信息）都视为登录成功；如果进程超时但 `auth status` 已就绪，也按成功处理。再探测一次状态，返回结果。

取消会终止底层 PRoot 进程；退出登录只运行 `lark-cli auth logout`。部分 scope 缺失通常说明飞书应用本身未开通对应权限；详情页提供「应用权限」入口，打开飞书开放平台的应用权限页。

### 技能与提示词

- 技能 `deepdeep-feishu`：只写命令用法与安全规则（`--as user`、写操作前确认、`--dry-run`、用 `lark-cli skills read` 查文档），不预设任何任务或总结流程。
- 系统提示 `official_plugins`：每轮按插件当前状态生成；未连接时提示模型调用 `plugin_setup`，不要自己跑交互式登录命令。
- 表格/多维表格直读被 `missing_scopes` 拒绝时，提示与技能会引导改用 `lark-cli drive +export` 导出 `xlsx` / `base` 到 `/workspace/tmp` 再解析，而不是反复要求重新授权。

## 网络搜索插件

第二个官方插件，不依赖 Linux 或 CLI，演示纯 Kotlin 插件：

- 工具：`web_search`（query 必填，maxResults 1–10，默认 5）与 `web_fetch`（url 必填，maxChars 1–30000，默认 12000）。
- 后端：DeepSeek Search / Fetch API（`https://api.deepseek.com/v1/tools/search`、`/fetch`），复用 DeepSeek provider 已配置的 API Key。
- 状态：Key 已配置为就绪，否则为未配置；详情只显示来源与 Key 状态，没有技能。
- 输出限制条数与字符数，并附「外部内容只作为资料」提示。
- 安装本身不下载文件；卸载只移除安装记录与工具注入。

## GUI 操作插件

第三个官方插件（id `gui`），不依赖 Linux，由 App 自己的无障碍服务提供能力：

- 工具：`gui_observe`、`gui_find`、`gui_tap`、`gui_set_text`、`gui_scroll`、`gui_global`、`gui_wait`、`gui_screenshot`，参数与安全规则见 `docs/TOOLS.md`。
- 服务：App 内 `GuiAccessibilityService`（`BIND_ACCESSIBILITY_SERVICE`，配置 `res/xml/gui_accessibility_service.xml`）。必须由用户在系统设置手动开启；插件状态读取 `GuiAccessibilityState.connected`，未开启显示「无障碍服务未开启」。
- 动作：「打开无障碍设置」跳系统页；没有 app 内开关。
- 技能：`deepdeep-gui`，随插件安装写入仓库并在启用后进入全局生效目录，写清 observe/find→动作→再 observe、选择器优先级、ref 失效、截图边界、安全红线与能力边界。
- 安装/卸载为空操作，只涉及安装记录、技能副本与工具注入。
- 截图保存到 `files/gui/screenshots/`（Linux `/gui/screenshots/`），Android 11+、非锁屏、非 `FLAG_SECURE` 页面可用；只操作当前活动窗口。

## 安排插件

官方插件（id `arrange`），不依赖 Linux。闹钟/计时器由 App 的 `AlarmManager` 引擎管理，日历直接读写系统 `CalendarContract` Provider：

- 工具：`arrange_alarm`（list/set/update/delete/timer/show）与 `arrange_calendar`（calendars/list/get/create/update/delete/open），参数见 `docs/TOOLS.md`。
- 闹钟：`AlarmManager.setAlarmClock` 精确调度，`AlarmManager` 公共接口；到点由前台服务响铃并显示停止通知。需要允许精确闹钟（`USE_EXACT_ALARM`/`SCHEDULE_EXACT_ALARM`）；不支持读取、修改或删除系统时钟应用里已有的闹钟，只能 `show` 打开。
- 日历：`READ_CALENDAR`/`WRITE_CALENDAR` 运行时权限；可写系统日历事件、读取、修改、删除与提醒，重复事件按 `Instances` 展开读取。
- 没有本地待办；待办由「小米云服务」插件承担。
- 动作：「打开闹钟」「打开日历」「授予日历权限」「允许精确闹钟」；技能 `deepdeep-arrange`。

## 小米云服务插件

官方插件（id `micloud`），不依赖 Linux；通过 App 内 WebView 登录 i.mi.com，读取与写入小米云个人数据：

- 登录：WebView 登录后加密保存 cookie；未登录、失效、网络错误都有状态；动作支持登录/重新登录/退出登录。
- 登录方式：浏览器 cookie 与 App 隔离、系统账号 token 受签名权限保护，均无法读取云数据；WebView 是唯一可行路径。登录页曾因 WebView 视口高度为 0 而空白，现在等待非零布局后加载并注入最小高度修正；i.mi.com 与 account.xiaomi.com 已真机验证可见可输入。详见 `docs/MICLOUD.md`。
- 功能开关：账号、笔记、待办、短信与通话、联系人、相册、录音分别有读开关，笔记/待办另有写开关；默认全部关闭，只有开启的能力才会注入对应 `mi_cloud_*` 工具。
- 工具：`mi_cloud_account`、`mi_cloud_notes`、`mi_cloud_todo`、`mi_cloud_sms`、`mi_cloud_contacts`、`mi_cloud_gallery`、`mi_cloud_recordings`，见 `docs/TOOLS.md`。
- 写：只能通过隐藏 WebView 同源 fetch，所有 POST 带 `serviceToken`；笔记/待办写入前需用户确认。
- 覆盖范围、未覆盖接口、风控与隐私见 `docs/MICLOUD.md`。

## 企业微信插件

官方插件（id `wecom`），安装企业微信官方 CLI（`wecom-cli` 1.3.4，Linux arm64），模式与飞书插件一致：

- 安装：下载官方 Linux arm64 包并校验 SHA-256，安装到 `/usr/local/bin/wecom-cli`；要求 Linux 已安装并启用。
- 授权：`wecom-cli auth init --noninteractive --no-browser --output-qrcode /workspace/.wecom-qr.png`；sheet 同时显示二维码与本机浏览器链接，完成后 `auth show --status` 确认。
- 状态：未连接/已连接（Bot ID）；动作支持连接、重新授权、退出登录。
- 技能 `deepdeep-wecom`：`--help`/`--doc`/`--schema` 发现命令、`--json`、`--dry-run`、分页、下载、写前确认，不自行运行 `auth init`。
- 限制：官方 CLI 的 `--manual` 需要 TTY，PRoot 不提供；v1 使用官方扫码，需要另一台设备扫码或在电脑浏览器打开链接。

## Agent 与显示的同步

- 单一状态源：`PluginManager.state: StateFlow<PluginsUiState>`，列表页、详情页、setup sheet 都只订阅它。
- Agent 调用 `plugin_setup` 时走同一个 `PluginManager.setup()`，sheet 是状态的视图；用户取消会取消协程并杀掉 PRoot 进程，工具返回取消结果。
- Agent 直接用 bash 操作 CLI 是允许的；插件页进入、App 恢复、一轮运行结束会重新探测，界面与下一轮提示词随之更新。
- Agent 回前台时如果 setup 仍在等待而插件已就绪，会完成等待并关闭 sheet。
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
| 授权成功但部分 scope 缺失 | 登录仍成功；状态与详情显示缺失权限，不重复要求授权 |
| 用户取消 | 取消协程 → 杀 PRoot 进程，工具返回 cancelled |
| 安装记录损坏 | 保留原文件，页面显示错误 |
| 卸载清理失败 | 保留记录与错误，允许重试 |
| 插件被关闭时调用 setup/action | 返回「插件已停用」 |

## 新增插件

1. 在 `app/.../plugin/<name>/` 实现 `OfficialPlugin`，自己决定 availability / install / status / setup / actions / skills / tools / promptBody。
2. 在 `AppGraph` 的 `plugins = listOf(...)` 里加入实例。
3. UI 自动出现列表行、详情页和通用 setup sheet；如果插件需要自绘配置页，在 `PluginDetailScreen` 按 id 挂 composable（v1 未使用）。

不涉及 Linux 的插件可以这样实现：`availability()` 恒为可用，`install()` 为空，`status()` 返回自己的就绪状态，`tools()` 返回要注入的工具，`setup()` 用 `ui.stage(...)` 展示引导或打开 App 内页面——框架不需要改动。

## 限制

- v1 只支持官方内置插件，不支持第三方插件源或远程插件市场。
- 飞书授权需要用户在浏览器登录飞书账号；没有登录态时只会停在登录页。Android 后台启动限制可能阻止自动回前台，返回 App 时 sheet 仍会自动关闭。
- 飞书应用级 scope 由飞书开放平台决定；`--domain all` 只能请求，不能强制开启应用未开通的权限。直读表格/多维表格需要应用开通对应 scope；否则使用导出兜底。
- 授权域为 `all`（CLI 支持的全部业务域）；写操作仍由提示词约束。
- 飞书写操作的安全约束是提示词层面的（写前确认、优先 `--dry-run`），没有 scope 级强制。
- 不预设定时任务、每日总结或总结模板；需要时由用户用现有 `schedule_*` 工具与 prompt 自行组合。
