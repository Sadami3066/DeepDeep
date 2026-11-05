# plugin/host/ui · 插件 UI 宿主（图标渲染由 `ui/plugins/PluginUiSupport.kt` 的 `PluginIcon` 负责）

插件 UI 贡献的宿主侧：槽位注册与解析、声明式节点渲染、Compose 槽宿主、图标解析与崩溃标记。插件只提供 `plugin-api` 数据（`PluginUiContributions`、`UiNode`、`SlotState`）；本目录不持有插件实例，也不做业务装配。

## 文件

| 文件 | 职责 |
|---|---|
| `SlotRegistry.kt` | 从 `PluginRegistrySnapshot` 解析槽位绑定：按 slotId 过滤、只保留可贡献相位、按 priority 降序 + 插件注册顺序排序、Compose 兼容性判定、解析每个绑定的 `assetsDir` |
| `PluginSlotHost.kt` | 槽位渲染入口：取第一个可渲染绑定，声明式交给渲染器、Compose 直接 `render(state)`；无绑定/不可渲染/上次崩溃时走 fallback；渲染前写崩溃标记、成功后清除 |
| `DeclarativeUiRenderer.kt` | `UiNode` 树 → Compose（column/row/text/markdown/image/button/link/progress/divider/spacer/qr_code），图片按插件 `assetsDir` 读本地文件 |

## 要点

- 槽位渲染只认第一个可渲染绑定（priority 大者优先、同优先按插件注册顺序），不是叠加渲染；宿主默认视图用 `fallback` 表达。
- `SlotRegistry` 的 Compose 兼容 = `uiApiLevel <= HOST_UI_API_LEVEL` + 清单 `ui.apiLevel` + 可选的 `ui.kotlin` 主次版本 / `ui.compose` 精确版本匹配；任一不满足只禁用该 Compose 绑定。
- 崩溃保护复用 `PluginFailureGuard`：key 为 `<pluginId>@<manifestVersion>:<slotId>`，残留标记表示上次渲染导致进程死亡，该版本走 fallback；插件版本升级后 key 变化会自动重试。
- 声明式 `UiNode` 渲染前用 `PluginValidation.validateUiNode` 校验（节点数/深度/字段），不合法绑定直接视为不可渲染并回退宿主默认 UI；`LINK` 节点用 `LocalUriHandler` 打开 URL。
- 本目录 composable 只接收 `assetsDir` 等数据依赖而不是 AppGraph 依赖，方便 Lead 直接接线与 JVM 单测纯逻辑。
