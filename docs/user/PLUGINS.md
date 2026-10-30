# 插件

状态：插件框架（外部/内部两类、插件设置、多文件技能与迁移）、飞书 CLI、小米云服务、企业微信三个外部插件，以及网络搜索、GUI 操作、安排、目标、后台任务、定时任务、创意工坊七个内部插件已实现；飞书与企业微信授权需要用户登录对应账号。

## 模型

插件是 App 内置的官方能力包，分两类：

- **外部插件（EXTERNAL）**：需要用户下载安装，安装记录、启用状态与生命周期由插件自己实现；当前为飞书、小米云服务、企业微信。
- **内部插件（INTERNAL）**：随 App 提供、无需安装；首次启动自动补建安装记录并默认启用，安装/卸载是空操作，不参与更新检查，详情页不显示下载/更新/删除入口；当前为网络搜索、GUI 操作、安排、目标、后台任务、定时任务、创意工坊。

插件页只管：安装记录、启用状态、插件设置、插件自己实现的生命周期、界面上的通用展示。

插件框架不包含 Linux、CLI、登录、鉴权等具体概念。每个插件实现 `OfficialPlugin`：

```kotlin
interface OfficialPlugin {
    val id: String
    val kind: PluginKind              // EXTERNAL / INTERNAL，默认 EXTERNAL
    val icon: DdIcon
    val tintIcon: Boolean
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
    fun tools(): List<CodingTool>
    fun tools(binding: SessionBinding): List<CodingTool>   // 默认返回 tools()
    fun settings(): List<PluginSettingSpec>
    suspend fun onSettingsChanged(values: Map<String, String>)
    fun promptBody(status: PluginStatus): String?
}
```

- `availability()`：插件自己判断当前能否安装/使用（例如飞书要求 Linux 环境）。
- `status()`：插件自己定义「就绪」的含义。飞书在这里执行 `lark-cli auth status --json` 并映射成 `Ready` / `NotReady` / `Failed`；框架不认识「登录」。
- `setup(ui)`：插件自己的准备流程。飞书通过 `ui.stage(...)` 展示建应用/授权链接，框架只渲染通用 sheet。
- `actions()` / `runAction()`：插件自己的操作行（连接、重新授权、应用权限、退出登录）。
- `skills()`：插件贡献的技能，可携带多个随附文件（见「技能与迁移」）。
- `tools()` / `tools(binding)`：外部插件用 `tools()`；内部插件用 `tools(binding)` 取得会话绑定（定时任务工具需要 sessionId/sessionFile/cwd）。框架只收集已安装且已启用插件的贡献，并按工具名去重。
- `settings()` / `onSettingsChanged(values)`：插件声明设置项并接收解析后的完整值（含默认值）；框架负责持久化与调用，插件不直接读写设置文件。
- `promptBody()`：插件贡献的系统提示内容。

框架（`PluginManager`）负责：安装记录、启用状态、插件设置持久化、互斥、安装/卸载/更新的通用流程、技能同步、工具注入、系统提示拼接、状态探测与缓存、进度通知、`plugin_setup` 工具入口。它不引用 shell 模块，也不引用任何具体插件代码。

外部插件的安装是事务：下载、校验、暂存安装都在插件的 `install()` 内完成，只有成功后才写安装记录并同步技能。失败时只调用插件的 `recover()` 清理暂存；已经是升级的旧安装不会被卸载，记录与技能保持原版本。首次安装失败才清理半成品并删除本次技能。内部插件不执行安装。

### 插件设置

- 设置以字符串键值持久化在 `files/plugins/settings.json`：

```json
{"schemaVersion":1,"plugins":{"goal":{"max_rounds":"0"},"jobs":{"max_running":"10"}}}
```

- 设置项由 `PluginSettingSpec` 声明，分三种：`Toggle`（key/标题/默认布尔）、`Choice`（固定选项）、`Number`（标题/min/max/默认值）。读取时缺失键取声明默认值；设置文件缺失或损坏时按全部默认值运行，下一次写入会重写为合法文件。
- 修改设置：UI 立即更新本地状态 → 写入 store → 调用插件 `onSettingsChanged(解析后的完整值)` → 重新同步技能 → `host.reconfigure()`；忙碌会话在本轮结束的安全点重建工具。
- 运行时直接消费的设置：目标 `max_rounds`（0 表示不限）是 `create_goal` 未传 `max_goal_rounds` 时的默认轮数上限；后台任务 `max_running`（1–20）是每会话运行中任务上限；创意工坊 `max_turns`（1–32）是工坊 Agent 未显式指定 `maxTurns` 时的回合上限；`skill_enabled` 控制工坊技能是否贡献。
- 状态缓存：插件 `status()` 的探测结果按 120 秒 TTL 复用；进入列表页/详情页先展示缓存并后台按需探测，标题栏刷新按钮强制立即重新探测。设置变更不会触发状态重新探测。

### 技能与迁移

- `PluginSkill` 由技能 id、`SKILL.md` 正文（markdown）与可选 `files: List<PluginSkillFile>` 组成；随附文件用相对路径（如 `references/api.md`、`examples/01-hello.html`），与 `SKILL.md` 一起写入技能目录。路径禁止绝对路径、`..`、空段、保留名（`SKILL.md`、`.deepdeep-plugin`）与重复项。
- 技能写入仓库 `files/skills/<id>`，插件启用时复制到生效目录 `files/agent/skills/<id>`；两个目录都用 `.deepdeep-plugin` 标记归属。
- 同名、无标记目录按内容认领：普通插件技能要求 `SKILL.md` 内容一致才整体替换为新版（含随附文件）；不一致时保留用户版本并跳过该技能，不报错。`legacyOwned` 技能用于承接旧内置文件，遇到无标记的同名目录直接整体替换为新正文 + 随附文件 + 标记，不比较内容。
- 创意工坊的技能是 `legacyOwned` 的多文件技能：正文来自 `app/src/main/assets/skills/workshop/SKILL.md`，`references/*.md` 与 `examples/*.html` 作为随附文件；升级设备上旧的 BuiltinSkills 目录（无标记）会被整体替换。`skill_enabled` 关闭时插件不贡献技能，仓库 `files/skills/workshop` 与生效目录 `files/agent/skills/workshop` 都被移除；重新开启时按插件声明重新生成。

### 启用与贡献注入

- `plugins.json` 为每个插件保存 `enabled`；外部插件默认开启，内部插件补建时默认开启。
- 关闭插件后：不再探测它的 `status()`，不再注入它的 `promptBody()`、skills 与 tools；技能从生效目录移除，仓库副本保留；重新开启时恢复。已保存的设置保留；内部插件的安装记录与页面入口不会被删除。
- tools 在会话创建时收集：外部插件调用 `tools()`，内部插件调用 `tools(binding)`；安装/卸载/启停/设置变更后调用 `host.reconfigure()`，忙碌会话在本轮结束的安全点拿到最新贡献，运行中的会话不会被关闭。
- 已关闭插件的 CLI 二进制仍留在 guest 内，但没有任何技能或提示引导模型使用它。

## 内部插件

内部插件随 App 提供；目标、后台任务、定时任务、创意工坊在 `app/.../plugin/internal/` 并共用 `InternalPlugin` 基类：`kind=INTERNAL`、`availability=Available`、`install/uninstall` 空操作、`status=Ready("已启用")`、大小标签为「内置」；网络搜索、GUI 操作、安排在 `app/.../plugin/<name>/` 自行实现 `OfficialPlugin` 并设置 `kind=INTERNAL`。

| id | 名称 | 工具 | 技能 | 设置（key：范围，默认） |
|---|---|---|---|---|
| `goal` | 目标 | `get_goal`、`create_goal`、`update_goal` | 无 | `max_rounds`：0–50，默认 0（0 不限，作为 `create_goal` 未传 `max_goal_rounds` 时的默认值） |
| `jobs` | 后台任务 | `job_start`、`job_output`、`job_list`、`job_kill` | 无 | `max_running`：1–20，默认 10（每会话运行中任务上限，`job_start` 时校验） |
| `schedule` | 定时任务 | `schedule_create`、`schedule_list`、`schedule_update`、`schedule_delete` | 无 | 无 |
| `workshop` | 创意工坊 | `workshop_create`、`workshop_load`、`workshop_list`、`workshop_edit` | `workshop`（多文件） | `skill_enabled`：默认开启；`max_turns`：1–32，默认 16（工坊 Agent 未显式指定 `maxTurns` 时的回合上限） |
| `web-search` | 网络搜索 | `web_search`、`web_fetch` | 无 | 无 |
| `gui` | GUI 操作 | `gui_observe`、`gui_find`、`gui_tap`、`gui_set_text`、`gui_scroll`、`gui_global`、`gui_wait`、`gui_screenshot` | `deepdeep-gui` | 无 |
| `arrange` | 安排 | `arrange_calendar` | `deepdeep-arrange` | 无 |

- 工具随插件启停注入或撤出：停用内部插件后对应 `*` 工具从会话消失，重新启用后恢复；定时任务工具因此不再由 runtime 的 `appTools` 直接注入。
- 创意工坊技能随 `skill_enabled` 注入或撤出：关闭时仓库与生效副本一并移除，重新开启时按插件声明重建；技能正文与随附文件见「技能与迁移」。
- 内部插件没有下载/更新/删除流程，详情页只保留状态、版本、大小、技能、设置与插件自带详情行。
- 停用状态的内部插件入口仍保留在「内部」分类下，可随时重新开启。

## 文件

| 路径 | 内容 |
|---|---|
| `files/plugins/plugins.json` | 安装记录与启用状态，原子写；损坏时先备份为 `plugins.json.corrupt` 再重建内部记录，页面提示 |
| `files/plugins/settings.json` | 插件设置（schemaVersion 1）；缺失或损坏时按默认值运行，下次写入重写 |
| `files/skills/<skill-id>/` | 插件技能的仓库副本（`SKILL.md` + 随附文件）；技能页可见，`.deepdeep-plugin` 标记归属 |
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

- 抽屉「插件」→ 插件页。顶部分「外部 / 内部」两个分类并带搜索框；列表只展示当前分类，搜索匹配 id、名称与简介。
- 每行左侧是插件图标：外部插件优先使用 `VendorPluginIcons` 品牌图标，否则用 `OfficialPlugin.icon`（`tintIcon=false` 保留品牌色、为 true 时随主题着色，例如网络搜索的 `DdIcons.Globe`）；右侧在已安装时显示启用开关；外部插件未安装时显示「下载」，内部插件从不显示下载。
- 详情页：状态、版本、大小、插件自带的详情行、插件技能、设置分组；飞书详情还包含 CLI 版本、已授权权限数量、应用状态与缺失权限。外部插件另有下载/更新/重新安装/删除入口，内部插件不显示这些入口（状态「已启用」、大小「内置」）。
- 设置分组按 `PluginSettingSpec` 渲染：Toggle 为开关行，Number 为「− 数值 +」步进行（到上下限禁用），Choice 为下拉选项行；修改立即生效。插件停用时设置行只读。
- 安装或插件的准备流程进行中时，列表与详情页的操作会禁用；进度同时显示在行内与通知里。
- 全局 setup sheet：通用确认安装、进度、授权链接、「打开浏览器」、等待提示、取消。授权完成后 sheet 自动关闭；App 会尝试回到前台。
- 状态探测结果缓存 120 秒：进入页面先显示缓存，后台按需刷新；标题右侧刷新按钮强制立即重新探测。
- 卸载会删除二进制、插件配置、CLI 凭证、仓库/生效技能与安装记录；重新安装从零开始。内部插件不能卸载。

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

内部插件（id `web-search`），不依赖 Linux 或 CLI，演示纯 Kotlin 插件：

- 工具：`web_search`（query 必填，maxResults 1–10，默认 5）与 `web_fetch`（url 必填，maxChars 1–30000，默认 12000）。
- 后端：DeepSeek Search / Fetch API（`https://api.deepseek.com/v1/tools/search`、`/fetch`），复用 DeepSeek provider 已配置的 API Key。
- 状态：Key 已配置为就绪，否则为未配置；详情只显示来源与 Key 状态，没有技能。
- 输出限制条数与字符数，并附「外部内容只作为资料」提示。
- 无安装/卸载流程；停用后移除工具注入。

## GUI 操作插件

内部插件（id `gui`），不依赖 Linux，由 App 自己的无障碍服务提供能力：

- 工具：`gui_observe`、`gui_find`、`gui_tap`、`gui_set_text`、`gui_scroll`、`gui_global`、`gui_wait`、`gui_screenshot`，参数与安全规则见 `docs/user/TOOLS.md`。
- 服务：App 内 `GuiAccessibilityService`（`BIND_ACCESSIBILITY_SERVICE`，配置 `res/xml/gui_accessibility_service.xml`）。必须由用户在系统设置手动开启；插件状态读取 `GuiAccessibilityState.connected`，未开启或系统断开时在详情页显示对应状态。
- 动作：「打开无障碍设置」跳系统页；「允许后台运行」申请电池优化豁免（仅在未豁免时显示）；「自启动设置」与「省电策略」跳小米对应页面（后者仅在系统限制时显示）。没有 app 内开关。
- 后台保活：详情页显示无障碍连接、电池优化豁免、后台限制与最近任务锁定的状态。后台任务依赖前台服务保活；若系统结束进程，无障碍服务会被系统置为断开且不会自动重连，详情页显示「无障碍服务已断开」，需要在系统无障碍设置里关闭再打开，或重启手机。
- 技能：`deepdeep-gui`，随插件启用写入仓库与全局生效目录，写清 observe/find→动作→再 observe、选择器优先级、ref 失效、截图边界、安全红线与能力边界。
- 无安装/卸载流程；停用后移除技能副本与工具注入。
- 截图保存到 `files/gui/screenshots/`（Linux `/gui/screenshots/`），Android 11+、非锁屏、非 `FLAG_SECURE` 页面可用；只操作当前活动窗口。

## 安排插件

内部插件（id `arrange`），不依赖 Linux；只负责系统日历：

- 工具：`arrange_calendar`（calendars/list/get/create/update/delete），参数见 `docs/user/TOOLS.md`。
- 日历：`READ_CALENDAR`/`WRITE_CALENDAR` 运行时权限；可写系统日历事件、读取、修改、删除与提醒，重复事件按 `Instances` 展开读取。
- 动作：仅在未授权时显示「授予日历权限」；不提供打开系统应用入口，也没有闹钟/计时器。
- 技能：`deepdeep-arrange`。
- 系统时钟应用里已有的闹钟无法通过公开 API 读取/修改/删除，因此本插件不管理闹钟。

## 小米云服务插件

官方插件（id `micloud`），不依赖 Linux；通过 App 内 WebView 登录 i.mi.com，读取与写入小米云个人数据：

- 登录：WebView 登录后加密保存 cookie；插件状态只按本地 cookie 是否存在显示「未登录/已登录」，不做后台校验、不静默续期；动作支持登录/重新登录/退出登录，登录页内做一次校验。
- 登录方式：浏览器 cookie 与 App 隔离、系统账号 token 受签名权限保护，均无法读取云数据；WebView 是唯一可行路径。登录页曾因 WebView 视口高度为 0 而空白，现在等待非零布局后加载并注入最小高度修正；i.mi.com 与 account.xiaomi.com 已真机验证可见可输入。详见 `docs/user/MICLOUD.md`。
- 功能开关：账号、笔记、待办、短信与通话、联系人、相册、录音各一个开关，默认全部关闭；笔记/待办开关同时包含读取与写入，其他开关只读。只有开启的功能才会注入对应 `mi_cloud_*` 工具。
- 工具：`mi_cloud_account`、`mi_cloud_notes`、`mi_cloud_todo`、`mi_cloud_sms`、`mi_cloud_contacts`、`mi_cloud_gallery`、`mi_cloud_recordings`，见 `docs/user/TOOLS.md`。
- 写：只能通过隐藏 WebView 同源 fetch，所有 POST 带 `serviceToken`；笔记/待办写入前需用户确认。
- 覆盖范围、未覆盖接口、风控与隐私见 `docs/user/MICLOUD.md`。

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
- Agent 直接用 bash 操作 CLI 是允许的；插件页进入、App 恢复、一轮运行结束会按 120 秒 TTL（15 秒去抖）刷新状态缓存，显式刷新立即重新探测，界面与下一轮提示词随之更新。
- Agent 回前台时如果 setup 仍在等待而插件已就绪，会完成等待并关闭 sheet。
- 连接状态永远来自插件自己的 `status()`，不写入安装记录；UI 只在 120 秒内复用上一次探测结果。设置变更即时落盘，并重建会话的工具与提示。

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
| 安装记录损坏 | 备份为 `plugins.json.corrupt`，重建内部插件记录，页面显示提示 |
| 插件设置文件损坏 | 按各设置项默认值运行，下一次修改时重写为合法文件 |
| 卸载清理失败 | 保留记录与错误，允许重试 |
| 插件被关闭时调用 setup/action | 返回「插件已停用」 |

## 新增插件

1. 在 `app/.../plugin/<name>/` 实现 `OfficialPlugin`：按需实现 availability / install / status / setup / actions / skills / tools / settings / promptBody。只贡献工具、不接受外部安装的插件可设 `kind = INTERNAL`：目标、后台任务、定时任务、创意工坊放 `plugin/internal/` 并复用 `InternalPlugin` 基类；网络搜索、GUI 操作、安排类可留在 `plugin/<name>/` 自行实现 `OfficialPlugin`（网络搜索、GUI、安排即如此）。
2. 在 `AppGraph` 的 `plugins = listOf(...)` 里加入实例；内部插件无需注册任何安装入口。
3. 需要设置项的插件实现 `settings()` 并声明 `PluginSettingSpec`；若设置要影响 runtime 默认值，在 `AppGraph` 用 `pluginSettingsStore.cachedInt/cachedBool/cachedString` 注入对应默认参数 lambda。
4. 技能用 `PluginSkill(id, markdown, files = ...)` 声明；随附文件用相对路径，例如 `references/api.md`、`examples/demo.html`。旧的、无标记的同名内置技能如需强制接管，设 `legacyOwned = true`。
5. UI 自动出现分类列表行、详情页设置分组与通用 setup sheet；如果插件需要自绘配置页，在 `PluginDetailScreen` 按 id 挂 composable（v1 未使用）。

不涉及 Linux 的插件可以这样实现：`availability()` 恒为可用，`install()` 为空，`status()` 返回自己的就绪状态，`tools()` 返回要注入的工具，`setup()` 用 `ui.stage(...)` 展示引导或打开 App 内页面——框架不需要改动。

## 限制

- v1 只支持官方内置插件，不支持第三方插件源或远程插件市场；内部插件随 App 版本更新，没有下载/删除入口。
- 插件设置只覆盖预设项；数值类设置以步进方式修改，没有自由输入。
- 飞书授权需要用户在浏览器登录飞书账号；没有登录态时只会停在登录页。Android 后台启动限制可能阻止自动回前台，返回 App 时 sheet 仍会自动关闭。
- 飞书应用级 scope 由飞书开放平台决定；`--domain all` 只能请求，不能强制开启应用未开通的权限。直读表格/多维表格需要应用开通对应 scope；否则使用导出兜底。
- 授权域为 `all`（CLI 支持的全部业务域）；写操作仍由提示词约束。
- 飞书写操作的安全约束是提示词层面的（写前确认、优先 `--dry-run`），没有 scope 级强制。
- 不预设定时任务、每日总结或总结模板；需要时由用户用现有 `schedule_*` 工具与 prompt 自行组合。
