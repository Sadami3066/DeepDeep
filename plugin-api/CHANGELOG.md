# Plugin API changelog

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
