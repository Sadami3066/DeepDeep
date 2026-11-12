# plugin-api · 插件契约 SDK

外部 `.ddp` 插件共用的唯一契约，也是对外发布的插件 SDK（Android library，包名 `io.github.whywood.deepdeep.plugin.api`）。只放契约类型与顶层工厂，不含宿主实现；manifest 解析/校验/版本比较已移到 `:app` 的 `plugin/host/external/`。`PLUGIN_API_LEVEL = 6`，`HOST_UI_API_LEVEL = 2`。

## 文件

| 文件 | 职责 |
|---|---|
| `PluginApi.kt` | 等级常量（`PLUGIN_API_LEVEL=6`、`BASELINE_API_LEVEL=6`、`MIN_SUPPORTED_API_LEVEL=6`、`HOST_UI_API_LEVEL=2`）与默认语言 |
| `Annotations.kt` | `@PluginExperimentalApi`（`@RequiresOptIn` 实验通道） |
| `Text.kt` | `LocalizedText` / `IconRef` 只读接口与 `localizedText()` / `assetIcon()` 工厂；外部插件不出现资源 id |
| `Capabilities.kt` | `PluginCapability` value class + 18 个常量（含 `PROMPT`）与 `fromWire` |
| `Manifest.kt` | `PluginManifest` / `UiManifest` / `PublisherInfo` / `Provides` 只读接口；宿主构造，插件不构造 |
| `Plugin.kt` | `DeepDeepPlugin`、`PluginHost`、`PluginSessionInfo`、`PluginSessionSnapshot`、`PluginAvailability`、`PluginSkill`、`PromptSection`、`PromptOverridePlugin` 与工厂 |
| `Contributions.kt` | `Contributions`、`PluginTool`、`PluginToolCall`、`PluginRunSignal`、`PluginToolResult`、`ToolBlock` 与工厂 |
| `Hooks.kt` | `PluginEvent` 标记接口、21 个观察事件接口、5 个变换/闸门请求与决策、`PluginHooks`（默认空实现） |
| `Ui.kt` | `SlotIds`、`SlotBinding`、`SlotContent`、`PluginPage`、`ComposerAction`、`PluginUiContributions` 与工厂 |
| `SlotStates.kt` | `SlotState` 标记接口与 `ToolCardSlotState` / `CustomMessageSlotState` 只读接口 |
| `Services.kt` | 17 个宿主服务接口、`PluginServiceUnavailable`、聊天 DTO 与工厂 |
| `ServiceModels.kt` | 服务 DTO：插件构造走接口+工厂，结果状态用 value class，宿主构造的结果保留 data class |
| `COMPATIBILITY.md` | 公开 ABI 承诺、允许/禁止改动、实验/弃用、兼容矩阵 |
| `compat/` | ABI 基线（`level-6.api`、allowlist） |

## 规则

- **构造走工厂**：插件需要构造的类型只提供接口 + 顶层工厂（`pluginTool`、`pluginSkill`、`promptSection`、`pluginPage`、`slotBinding`、`textResult`、`localizedText`、`timerRequest` 等）；不要新增公开 data class 构造器或把内部实现暴露成公共类型。
- **事件走接口**：`PluginEvent` 是非 sealed 标记接口，每个事件是宿主实现的只读接口；新增事件用新的 `PluginHooks` 默认方法，不往旧 sealed 里加子类型。
- **状态用 value class**：可能增长的结果状态（`MessageRole`、`ToolPhase`、`JobStatus`、`GoalPhase`、`McpServerStatus`、`ChatSendKind` 等）用 `@JvmInline value class` + 常量；请求侧闭合枚举保留。
- **实验能力**：prompt 覆盖等新能力标 `@PluginExperimentalApi`，转正时只去注解、不改签名。
- explicit API 模式开启：新增公共声明必须显式 `public`，内部实现保持 `internal`。
- 改公开类型/签名前后跑 `./scripts/api-compat.sh`；破坏性变更必须升 `PLUGIN_API_LEVEL`、更新兼容基线并写 `CHANGELOG.md`。
- 测试在 `src/test/kotlin`（JUnit4），运行 `./scripts/gradlew-locked :plugin-api:testDebugUnitTest`；`./scripts/gradlew-locked :plugin-api:publishAllPublicationsToPagesRepository` 发布 SDK 到 `build/maven-repo`，再复制到 `deepdeep-maven` 仓库。
