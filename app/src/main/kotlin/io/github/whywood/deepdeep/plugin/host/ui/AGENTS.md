# plugin/host/ui · 插件 UI 宿主（图标渲染由 `ui/plugins/PluginUiSupport.kt` 的 `PluginIcon` 负责）

插件 UI 贡献的宿主侧：槽位注册与解析、Compose 槽宿主、宿主版本探测。插件只提供 `plugin-api` 数据（`PluginUiContributions`、`SlotContent`、`SlotState`）；本目录不持有插件实例，也不做业务装配。

## 文件

| 文件 | 职责 |
|---|---|
| `SlotRegistry.kt` | 从 `PluginRegistrySnapshot` 解析槽位绑定：`bindings(slotId)` 精确取槽、`entriesOf(pluginId)` 取插件全部绑定；保留可贡献相位、按 priority 降序 + 插件注册顺序排序；Compose 兼容性判定给出 `SlotDisabledReason` |
| `HostSlotStates.kt` | `SlotState` 宿主实现（`HostToolCardSlotState`/`HostCustomMessageSlotState`），供聊天渲染构造槽位状态 |
| `PluginSlotHost.kt` | 槽位渲染入口：取第一个可渲染绑定直接 `render(state)`；无绑定/不兼容时走 fallback；渲染异常由 Compose 直接抛出（无进程级隔离） |
| `ComposeVersionDetector.kt` | 宿主 Compose runtime 版本探测：优先读 APK 内 `META-INF/androidx.compose.runtime_runtime.version`；读不到时反射 `ComposeVersion.version`（int 兼容编号或字符串版本） |

## 要点

- 槽位渲染只认第一个可渲染绑定（priority 大者优先、同优先按插件注册顺序），不是叠加渲染；宿主默认视图用 `fallback` 表达。
- Compose 兼容条件只看清单 `ui` 块：`ui.apiLevel <= HOST_UI_API_LEVEL`、`ui.kotlin` 填了则主次版本一致、`ui.compose` 填了则与宿主 Compose runtime 版本精确一致；不兼容时 `SlotRenderEntry.disabledReason` 带具体原因（`SlotDisabledReason`）。
- `ui.compose` 比较用宿主 runtime 的 maven 版本（如 `1.10.5`，来自 APK 版本文件），不是 Compose BOM 版本；`ComposeVersion.version` 是编译器兼容编号（int）而非版本号，只能作为探测失败时的诊断信息，不能拿来当版本比较。
- 槽内容统一是 `SlotContent` 接口的 `render(state)`，宿主不再区分声明式子类型；插件页面用 `PluginPage.content()`。
- 插件 UI 只有 Compose 一条轨道，没有声明式节点校验与降级回退。
- 本目录 composable 只接收 `assetsDir` 等数据依赖而不是 AppGraph 依赖，方便 Lead 直接接线与 JVM 单测纯逻辑。
