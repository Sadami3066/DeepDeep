# plugin-api · 插件契约 SDK

内置插件与外部 `.ddp` 插件共用的唯一契约，也是对外发布的插件 SDK（Android library，包名 `io.github.whywood.deepdeep.plugin.api`）。只放类型与校验，不含宿主实现；`apiLevel = 1`。

## 文件

| 文件 | 职责 |
|---|---|
| `PluginApi.kt` | API/UI 等级常量、默认语言、`PluginSource` |
| `Text.kt` | `LocalizedText`（内置用 `Resource`、外部用 `Literal`）、`IconRef` |
| `Manifest.kt` | `PluginManifest` / `Provides` / `PublisherInfo` / `UiManifest` |
| `PluginVersion.kt` | `major.minor.patch` 解析与比较，后缀保留但不参与比较 |
| `PluginValidation.kt` | 格式/ABI/技能/UiNode 校验，返回带 `Severity` 的 `Issue` 列表 |
| `PluginManifestCodec.kt` | `plugin.json`（`JsonObject`）→ `PluginManifest`；`parse` 宽容、`validate` 严格、`decode` 汇总为 `Result` |
| `Plugin.kt` | `DeepDeepPlugin` 生命周期、`PluginHost` 服务入口、live 会话句柄 `PluginSessionInfo`/`PluginSessionSnapshot`、状态/进度/setup/动作/详情行/技能/提示词段 |
| `Contributions.kt` | `Contributions(tools, hooks)`、`PluginTool` 与工具结果 DTO |
| `Hooks.kt` | `PluginEvent` 事件层级、4 类决策（输入/工具调用/工具结果）与变换请求、`PluginHooks` |
| `Settings.kt` | `SettingSpec`（Toggle/Choice/Number/Text/Group）+ `normalize` |
| `Ui.kt` | 槽位目录 `SlotIds`、`SlotBinding` / `SlotContent`、声明式 `UiNode` 与节点类型、页面/输入区动作 |
| `SlotStates.kt` | 各槽位族的状态 DTO 与 `SlotState` / `NoSlotState` |
| `ServiceModels.kt` | 宿主服务 DTO：Linux/特权/屏幕/通知/网络/文件/会话/job/timer/goal/工坊/MCP/App/总线 |
| `Services.kt` | 宿主服务接口与 `PluginServiceUnavailable` |

测试在 `src/test/kotlin`（JUnit4），覆盖 manifest 编解码与校验、版本比较、UiNode/槽位、设置 normalize、hooks 默认值；运行 `./scripts/gradlew-locked :plugin-api:testDebugUnitTest`。

对外发布：`./scripts/gradlew-locked :plugin-api:publishToMavenLocal`（group `io.github.whywood.deepdeep`，artifact `plugin-api`，版本 `pluginApiVersion` 默认 `0.3.0`；发布前 ABI 可随时删改且不升级 `PLUGIN_API_LEVEL`，发布后才按「只做加法 + 改 ABI 升等级」执行）。

## ABI 规则（对外承诺）

- `PLUGIN_API_LEVEL` 现在是 1。**发布前没有兼容承诺**：当前可随时删改类型/签名且不升 level，插件必须按最新 SDK 重编译；发布后才启用「同一 level 只允许加法、删改变必须升 level、宿主拒绝 `apiLevel > 宿主`」的规则。
- `PluginSessionInfo` 是宿主持有的 live handle：`contributionsFor` 拿到它时不要缓存/拷贝字段，需要会话时在工具/动作执行回调里调 `snapshot()`；`PluginToolCall.sessionId` 也由宿主在 execute 时解析。
- `minHostVersion` 用 `PluginVersion` 比较，只看 major.minor.patch，后缀忽略（`0.2.0-dev` 视为 `0.2.0`），避免开发版被自己的 `minHostVersion` 卡住。
- `ui.apiLevel` 与宿主 `HOST_UI_API_LEVEL` 不匹配时只禁用该 Compose 绑定并回退声明式，不拒绝插件；声明式 `UiNode` 才是跨版本稳定面。
- 插件依赖必须 `compileOnly`，禁止打包 `plugin-api`、Compose、coroutines、kotlinx-serialization；宿主 parent-first，两边共享同一份类。
- 字符串/图标不走资源 id：内置插件可用 `LocalizedText.Resource` / `IconRef.Resource`（同 APK 内），外部插件只能用 `Literal` 与 `asset:` 路径；校验器按 `PluginSource` 区分。
- 宿主服务不可用抛 `PluginServiceUnavailable`，不返回 null；`requires` 只用于安装提示与可用性判定，不做运行时拦截（同进程无法收窄权限）。
- 工具 DTO（`PluginTool`/`PluginToolResult`）是本模块自有类型，宿主在边界转成 pi SDK 的 `CodingTool`；`:runtime` 不依赖本模块，避免把 SDK ABI 绑进运行时。

## 要点

- `PluginManifestCodec` 必须是纯 JVM（不 import Android），宿主装载器只解析一次；未知字段忽略，错误标量类型退化为默认值交给 `validate` 报 `Issue`。
- 槽位目录：`setup.content`、`plugin.detail.section:<sectionId>`、`tool.card.content:<toolName>`、`chat.message:<customType>`；页面与输入区动作分别用 `PluginPage` / `ComposerAction`，不是 `SlotBinding`。实例后缀必填且要过 `SlotIds.matches`。
- `PluginHooks` 22 个通知回调按 `OBSERVE_TIMEOUT_MS`（5s）派发，4 个变换/闸门按 `TRANSFORM_TIMEOUT_MS`（2s）；默认实现全部 no-op，`PluginHooks.None` 是一个空实现。闸门失败按 fail-closed 由宿主决定。
- `SettingSpec.Group` 只是 UI 分组容器，不落值；`List<SettingSpec>.normalize` 按叶子 key 补默认值并丢弃非法存值。
- `UiNode` 支持 11 种节点（`UiNodeTypes`），深度 ≤ 8、节点数 ≤ 200、props 未知即警告，具体规则在 `PluginValidation.validateUiNode`。
- `PluginHost` 暴露 17 个宿主服务访问器（linux/privilege/screen/notifications/secrets/network/files/sessions/jobs/timers/goals/workshop/mcp/ui/app/bus/logs），另保留 `manifest/context/dataDir/scope/text/log` 旧签名；`text` 的 `vararg args: Any` 是 P0 兼容保留，格式化参数只接受字符串与数字，新 API 不再引入 `Any`。
- 本模块没有 res 与用户可见文案；诊断信息是开发侧英文，用户可见字符串由宿主用 `host.text` 解析 `LocalizedText`。
