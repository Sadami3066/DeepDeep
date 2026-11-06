# plugin-api · 插件契约 SDK

外部 `.ddp` 插件共用的唯一契约，也是对外发布的插件 SDK（Android library，包名 `io.github.whywood.deepdeep.plugin.api`）。只放类型与校验，不含宿主实现；`apiLevel = 4`。

## 文件

| 文件 | 职责 |
|---|---|
| `PluginApi.kt` | API/UI 等级常量（`PLUGIN_API_LEVEL=4`、`BASELINE_API_LEVEL=4`、`MIN_SUPPORTED_API_LEVEL=4`、`HOST_UI_API_LEVEL=1`）与默认语言 |
| `Text.kt` | `LocalizedText`（`Literal` 为唯一可用形态，`Resource` 分支保留给宿主内部）、`IconRef`（`Asset` 为唯一可用形态，`Resource` 分支保留） |
| `Manifest.kt` | `PluginManifest` / `Provides`（tools/skills/uiSlots/pages）/ `PluginCapability`（18 个宿主服务）/ `PublisherInfo` / `UiManifest` |
| `PluginVersion.kt` | `major.minor.patch` 解析；完整 semver 比较（release > prerelease、build 忽略）与只比内核的 `compareCore` |
| `PluginValidation.kt` | 格式/ABI/技能校验，返回带 `Severity` 的 `Issue` 列表；id 只接受反向域名，文本只接受 `Literal`、图标只接受 asset 路径 |
| `PluginManifestCodec.kt` | `plugin.json`（`JsonObject`）→ `PluginManifest`；`parse` 宽容、`validate` 严格、`decode` 汇总为 `Result` |
| `Plugin.kt` | `DeepDeepPlugin` 生命周期（availability/uninstall/onEnable/onDisable/onUpgrade/skills/promptSections/contributions/contributionsFor/ui/close）、`PluginHost` 服务入口、live 会话句柄 `PluginSessionInfo`/`PluginSessionSnapshot`、`PluginAvailability` 与 `isReady` |
| `Contributions.kt` | `Contributions(tools, hooks)`、`PluginTool` 与工具结果 DTO |
| `Hooks.kt` | `PluginEvent` 事件层级、4 类决策（输入/工具调用/工具结果）与变换请求、`PluginHooks` |
| `Ui.kt` | 槽位目录 `SlotIds`（tool.card/chat.message）、`SlotBinding` / `SlotContent.Compose`、`PluginPage`（页面内容内联）、`ComposerAction` |
| `SlotStates.kt` | 槽位状态 DTO（`ToolCardSlotState`/`CustomMessageSlotState`）与 `SlotState`/`NoSlotState` |
| `ServiceModels.kt` | 宿主服务 DTO：Linux/特权/屏幕/通知/网络/文件/会话/job/timer/goal/小程序/MCP/App/总线 |
| `Services.kt` | 宿主服务接口、`ChatService.sendMessage` 投递 DTO 与 `PluginServiceUnavailable` |

测试在 `src/test/kotlin`（JUnit4），覆盖 manifest 编解码与校验、版本比较（semver 与 `compareCore`）、槽位 ID 规则、capabilities 校验、hooks 默认值；运行 `./scripts/gradlew-locked :plugin-api:testDebugUnitTest`。公开 ABI 由 `scripts/plugin-api-abi.sh` + `plugin-api/abi/` 的 golden 清单固定，删改公开类型/签名必须显式更新清单并升版本。

对外发布：`./scripts/gradlew-locked :design:publishToMavenLocal :plugin-api:publishToMavenLocal`（group `io.github.whywood.deepdeep`，版本 `pluginApiVersion` 默认 `0.7.0`；`design` 是 App 的设计系统（当前 `0.6.0`），通过 `api(project(":design"))` 成为插件 UI 的编译期依赖，版本独立维护。发布前 ABI 可随时删改且不升级 `PLUGIN_API_LEVEL`，发布后才按「只做加法 + 改 ABI 升等级」执行）。

## ABI 规则（对外承诺）

- `PLUGIN_API_LEVEL` 现在是 4，`MIN_SUPPORTED_API_LEVEL = 4`；宿主只接受 `4..4` 的插件。**发布前没有兼容承诺**：当前可随时删改类型/签名且不升 level，插件必须按最新 SDK 重编译；发布后才启用「同一 level 只允许加法、删改变必须升 level、宿主拒绝 `apiLevel > 宿主`」的规则。
- `BASELINE_API_LEVEL = 4`：manifest 未写 `apiLevel` 时按此值处理，`PluginManifestCodec.parse` 用它兜底；`PluginManifest` 程序化构造的默认值仍是 `PLUGIN_API_LEVEL`。
- `minHostVersion` 用 `PluginVersion.compareCore` 比较，只看 major.minor.patch、忽略后缀（`0.2.0-dev` 视为 `0.2.0`），避免开发版被自己的 `minHostVersion` 卡住；完整 `compareTo` 按 semver 处理预发布与 build 元数据。
- `PluginSessionInfo` 是宿主持有的 live handle：`contributionsFor` 拿到它时不要缓存/拷贝字段，需要会话时在工具回调里调 `snapshot()`；`PluginToolCall.sessionId` 也由宿主在 execute 时解析。
- `ui.apiLevel` 与宿主 `HOST_UI_API_LEVEL` 不匹配、或 `ui.kotlin`/`ui.compose` 与宿主不一致时，只禁用该插件的 UI（`pagesOf` 返回空、槽位不渲染），不拒绝插件本身。
- 插件依赖必须 `compileOnly`，禁止打包 `plugin-api`、Compose、coroutines、kotlinx-serialization；宿主 parent-first，两边共享同一份类。
- 字符串/图标不走资源 id：外部插件只能用 `LocalizedText.Literal` 与 `icon: asset:<path>`；校验器拒绝 `LocalizedText.Resource` / `IconRef.Resource`。
- 宿主服务未在 manifest `capabilities` 声明、或已声明但当前不可用，均抛 `PluginServiceUnavailable`（`reason` 区分 `not declared in manifest` / `unavailable`），不返回 null。
- 工具 DTO（`PluginTool`/`PluginToolResult`）是本模块自有类型，宿主在边界转成 pi SDK 的 `CodingTool`；`:runtime` 不依赖本模块，避免把 SDK ABI 绑进运行时。

## 要点

- `PluginManifestCodec` 必须是纯 JVM（不 import Android），宿主装载器只解析一次；未知字段忽略，错误标量类型退化为默认值交给 `validate` 报 `Issue`。
- 槽位目录只有 `tool.card.content:<toolName>` 与 `chat.message:<customType>` 两类；整页界面用 `PluginPage(id, title, icon?, content)` 声明，内容内联，输入区动作用 `ComposerAction`，都不是 `SlotBinding`。实例后缀必填且要过 `SlotIds.matches`；页面 id 允许点号。
- 插件 UI 只有自由 Compose 一条轨道：`SlotContent.Compose(render: @Composable (SlotState) -> Unit)` 用于工具卡/自定义消息槽位，`PluginPage.content` 用于整页界面；宿主不再有声明式节点渲染。插件 UI 直接用 App 的 `:design` 设计系统（`DdGroupCard`/`DdListRow`/`DdPrimaryButton`/`DdTextField`/`DdSwitch`/`DdType`/`deepDeepColors()` 等），与 App 视觉完全同源；`design` 由本模块 `api()` 传递，插件仍 `compileOnly` 不打包，运行时由宿主 parent classloader 解析。
- `PluginHooks` 21 个通知回调按 `OBSERVE_TIMEOUT_MS`（5s）派发，4 个变换/闸门按 `TRANSFORM_TIMEOUT_MS`（2s）；默认实现全部 no-op，`PluginHooks.None` 是一个空实现。闸门失败按 fail-closed 由宿主决定。
- 插件不再有安装/连接/状态/设置/动作/详情行的声明式面；外部包由宿主安装，插件自己的 CLI 下载、登录与状态管理由插件页面自行完成。
- `PluginHost` 暴露 18 个宿主服务访问器（linux/privilege/screen/notifications/secrets/network/files/sessions/jobs/timers/goals/miniapp/mcp/ui/app/bus/logs/chat）与 `requestContributionsRefresh()`（插件页面改完状态后请求宿主重物化贡献并重建会话），另保留 `manifest/context/dataDir/assetsDir/scope/text` 旧签名；`text` 的 `vararg args: Any` 是 P0 兼容保留，格式化参数只接受字符串与数字，新 API 不再引入 `Any`。
- 本模块没有 res 与用户可见文案；诊断信息是开发侧英文，用户可见字符串由宿主用 `host.text` 解析 `LocalizedText`。
