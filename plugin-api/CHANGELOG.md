# Plugin API changelog

## 1.4.0 / API level 6

加法版本，`PLUGIN_API_LEVEL` 仍为 6：接口只增不改，已发布插件无需重新编译；使用本版本新能力的插件需 `minHostVersion >= 1.4.3`。

- 卡片交付归插件侧：新增 `Cards` 对象（`KEY`/`RAW_KEY`/`GUIDANCE_KEY`/`DISPLAY_KEY`、`descriptor(...)`、`instruction(callId, title)` 与 `deliver(callId, result)`）。`PluginTool` 工厂会自动把结果 details 里的 `deepdeep.card`、`deepdeep.raw`、`deepdeep.guidance` 组装成模型可见文本（完整返回、使用说明、`present` 调用指令），并把展示文本写进 `deepdeep.display`；宿主不再追加卡片 id 或拼接任何卡片文案。
- `Cards.deliver(callId, result)`：插件把卡片描述符、完整返回与使用说明写进结果 details，由 SDK 生成模型文本（含 `present` 指令）与展示文本（`deepdeep.display`）；`PluginTool` 工厂自动调用，重复调用幂等，`richResult(blocks)` 也支持。
- 兼容性：`design` 仍为 0.11.0；新增项均为加法，旧插件二进制不受影响。

## 1.3.0 / API level 6

加法版本，`PLUGIN_API_LEVEL` 仍为 6：接口只增不改，已发布插件无需重新编译；使用本版本新能力的插件需 `minHostVersion >= 1.1.0`。

- 工具结果渲染器：新增 `ToolResultSource`（value class：`PLUGIN`/`MCP`/`BUILTIN`）、`ToolResultMatch` + `toolResultMatch(...)`、`ToolResultRenderer` + `toolResultRenderer(...)`、`ToolResultState`，以及可选的 `ToolResultRendererContributor`。
- `PluginUiContributions` 不变；插件在 `ui()` 返回的对象上额外实现 `ToolResultRendererContributor` 即可贡献渲染器，旧插件二进件不受影响。
- 插件可以给任意工具结果（包括 Agent 通过 `tool_execute` 调用的 MCP 工具）提供 Compose 渲染，不再只限插件自己的工具名；`ToolResultState` 提供参数、`result`、`structuredContent` 与工具来源。
- 匹配维度：`source`、MCP `server`、`tool`、`toolPrefix`；优先级、匹配具体度和插件注册顺序决定唯一渲染器；禁用/不兼容的插件自动回退到默认工具卡。
- `McpService.callStructured(...)`：新增 `McpStructuredCallResult`，插件自己调用 MCP 时也能拿到 `blocks` 与 `structuredContent`；未实现该能力时默认退回 `call(...)` 且 structured 为 null。
- 结构化结果：宿主把 MCP `structuredContent` 持久化到工具详情（上限 256 KiB），MCP 结果的 `tool_execute` 包装会自动还原内部工具身份；没有结构化结果时渲染器仍可只依赖文本/参数。
- 兼容性：`design` 仍为 0.9.0；MCP 工具结果渲染依赖宿主 1.1.0+。

## 1.2.0 / API level 6

加法版本，`PLUGIN_API_LEVEL` 仍为 6：接口只增不改，已发布插件无需重新编译即可在宿主 1.0.8+ 继续运行；使用本版本新能力的插件需 `minHostVersion >= 1.0.8`。

- manifest：`Provides` 新增 `themes: List<String>`（默认空列表，声明插件可提供的主题）；`Provides.isEmpty` 一并计入 themes。
- 页面：`UiService.openPageSheet(pageId, arguments)`（默认实现退回 `openPageWithArguments`）；新增 `PluginPagePresentation`（`PAGE`/`SHEET`，含 `fromWire`）与 `PluginPageContext.presentation`（默认 `PAGE`）。
- 兼容性：`design` 需 0.9.0+（`DeepDeepTheme(colors, fontStyle)` 重载、`DdTheme.lightDeepDeep()`、`DdStatusRow`/`DdNoticeBar`/`DdProgressBar`）；新增 API 均有宿主实现或明确的不可用降级。

## 1.1.0 / API level 6

加法版本，`PLUGIN_API_LEVEL` 仍为 6：接口只增不改，已发布插件无需重新编译即可在宿主 1.0.6+ 继续运行；使用本版本新能力的插件需 `minHostVersion >= 1.0.6`。

- 新能力（manifest `capabilities`，共 20 个新 wire）：`settings`、`device`、`clipboard`、`permissions`、`packages`、`privilege`、`intents`、`components`、`background`、`location`、`contacts`、`sms`、`calendar`、`media`、`speech`、`web`、`credentials`、`accessibility`、`share`、`deeplink`。
- `PluginHost` 新增访问器：`settings()/device()/clipboard()/permissions()/packages()/privilege()/intents()/components()/background()/location()/contacts()/sms()/calendar()/media()/speech()/web()/credentials()/accessibility()`；`components()`/`credentials()`/`accessibility()` 标 `@PluginExperimentalApi`。
- 设备与系统：设备信息与状态流、剪贴板、运行时权限与特殊授权、应用列表/安装/卸载/启动、Root/ADB 状态与提权执行、任意 Intent 与动态广播接收、独立窗口（`PluginHostActivity`）与宿主前台服务、WorkManager/AlarmManager 后台任务与开机回调。
- 数据与内容：插件级设置存储、定位与地理编码、联系人读写、短信读写与发送、系统日历读写、媒体保存/分享/预览/图片读取、语音识别与朗读、宿主网页搜索/抓取、宿主服务商与 MCP 凭据读取（实验）。
- UI：`UiService` 新增 `openPageWithArguments`/`closePage`/`pageContext`/`confirm`/`prompt`/`choose`/`pickDate`/`pickTime`/`pickFiles`/`pickImage`/`scanQr`/`pickDirectory`/`snackbar`/`shareFiles`/`isDark`；新增 `LocalPluginPageContext` 与 `PluginPageContext`；新增 `PluginPickedFile` 与交互请求模型。
- 通知：`NotificationService` 新增 `notifyChecked`、`listenerGranted`、`recent`、`received`、`removed`、`dismiss`；`PluginNotification` 支持 `actions`/`payload`（`pluginNotificationWithActions(...)`），动作经 hooks `notificationAction(...)` 回调。
- 会话与聊天：`SessionService` 新增 `history`/`stop`/`create`/`rename`/`delete`/`open`/`archive`；`ChatService` 新增 `sendMessageWithAttachments(...)`。
- 事件 hooks：新增 `notificationReceived`/`notificationAction`/`backgroundTask`/`shareReceived`/`deepLinkReceived`/`appLifecycle`（后两者与 share/background 由 capability 控制投递）。
- manifest：新增可选 `permissions`（`PluginPermission` 常量，声明插件会请求的运行时权限）与 `components`（后台、前台服务、通知监听、无障碍、分享接收、深链接路径）；宿主安装期校验权限与 capability 的一致性。
- 兼容性：`design` 需 0.7.0+（新增 Markdown/WebView/图片/HTML/刷新/日期时间/Snackbar/文件行等组件）；新增 API 均有宿主实现或明确的不可用降级。

## 1.0.1 / API level 6

补丁版本，契约与 apiLevel 不变。

- 修复 `PluginTool` 工厂的执行递归（`PluginToolImpl.execute` 自调用导致栈溢出）。
- 发布纪律：从该版本起已发布坐标不可覆盖，后续修复只递增补丁版本；曾解析过被覆盖前 `1.0.0` AAR 的机器升级到 `1.0.1`，或执行 `--refresh-dependencies`。

## 1.0.0 / API level 6

破坏性升级，不保留 level 5 兼容层。

- 契约等级：`PLUGIN_API_LEVEL = 6`、`BASELINE_API_LEVEL = 6`、`MIN_SUPPORTED_API_LEVEL = 6`、`HOST_UI_API_LEVEL = 2`；宿主拒绝 level 5 插件并提示更新。
- 构造方式：插件构造的 DTO 改为接口 + 顶层工厂（`pluginTool`、`pluginSkill`、`promptSection`、`pluginPage`、`slotBinding`、`contributions`、`textResult`、`localizedText`、`timerRequest` 等）；不再提供公共 data class 构造器。
- 事件与 hooks：`PluginEvent` 改为非 sealed 标记接口，21 个观察事件改为宿主实现的只读接口；决策类型改为接口 + 工厂（`inputContinue`、`toolBlock`、`resultReplace` 等）。
- 枚举收敛：`PluginCapability` 改为 value class；`MessageRole`、`ToolPhase`、`JobStatus`、`GoalPhase`、`McpServerStatus`、`ChatSendKind` 等结果状态改为 value class + 常量。
- 文本与图标：删除 `LocalizedText.Resource` / `IconRef.Resource`，只保留 `localizedText()` / `assetIcon()` 工厂。
- 新增能力：manifest capability `prompt`；`PromptOverridePlugin` / `PromptOverride`（preamble/rules/sections/priority，实验 API）。
- Host Surface：`PluginHost.text(source, vararg args: Any)`（格式化参数只接受字符串与数字）；manifest 类型改为只读接口。
- 工程：移动 manifest codec / validation / version 到宿主；开启 explicit API；新增 `scripts/api-compat.sh`（plugin-api 与 design ABI 只增不减门禁）与 `COMPATIBILITY.md` / `compat/` 基线。

## 0.8.1 / API level 5

- 重发 AAR，补齐 AndroidX consumer keep 规则；依赖 design 0.6.1。
