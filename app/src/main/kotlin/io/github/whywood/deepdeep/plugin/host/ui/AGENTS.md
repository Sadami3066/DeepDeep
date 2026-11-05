# plugin/host/ui · 插件 UI 宿主

插件 UI 贡献的宿主侧：槽位注册与解析、声明式节点渲染、Compose 槽宿主、崩溃标记、页面/输入区动作/自定义消息宿主，以及给插件管理 UI 用的纯数据目录。插件只提供 `plugin-api` 数据（`PluginUiContributions`、`UiNode`、`SlotState`）；本目录不持有插件实例，也不做业务装配。

## 文件

| 文件 | 职责 |
|---|---|
| `SlotRegistry.kt` | 从 `PluginRegistrySnapshot` 解析槽位绑定：按 slotId 过滤、只保留可贡献相位、按 priority 降序 + 插件注册顺序排序、Compose 兼容性判定、解析每个绑定的 `assetsDir` |
| `PluginSlotHost.kt` | 槽位渲染入口：取第一个可渲染绑定，声明式交给渲染器、Compose 直接 `render(state)`；无绑定/不可渲染/上次崩溃时走 fallback；渲染前写崩溃标记、成功后清除 |
| `DeclarativeUiRenderer.kt` | `UiNode` 树 → Compose（column/row/text/markdown/image/button/link/progress/divider/spacer/qr_code），图片按插件 `assetsDir` 读本地文件 |
| `PluginUiCatalog.kt` | 纯数据：`PluginUiPlugin`（manifest 元信息 + pages/composerActions/bindings + assetsDir + 相位）、`PluginUiCatalog` 查询、`PluginUiCatalogProvider` 与 `pluginUiCatalogProvider(registry, pluginsRoot)` |
| `PluginPageHost.kt` | `PluginPageResolver`（按插件列页面/按 id 解析）、页面槽位约定 `plugin.page:<pageId>`、`PluginPageHost` 渲染页面内容 + `PluginPageState` 构造 |
| `PluginComposerActionHost.kt` | `PluginComposerActionResolver`（可贡献相位的输入区动作）、`ComposerActionHost`/`ComposerActionBar` 图标+文字胶囊，回调带 pluginId + actionId |
| `CustomMessageRenderer.kt` | `PluginCustomMessage` 输入、`PluginCustomMessageTypes`（slotId/customType 校验/payload pretty-print）、`CustomMessageRenderer`（`chat.message:<customType>` 槽 + payload 兜底卡） |
| `PluginIconHost.kt` | `IconRef` → Compose：`Resource` 走 DdIcon，`Asset` 按 `assetsDir` 解码本地图片（路径安全校验，失败不渲染） |

## 要点

- 槽位渲染只认第一个可渲染绑定（priority 大者优先、同优先按插件注册顺序），不是叠加渲染；宿主默认视图用 `fallback` 表达。
- `SlotRegistry` 的 Compose 兼容 = `uiApiLevel <= HOST_UI_API_LEVEL` + 清单 `ui.apiLevel` + 可选的 `ui.kotlin` 主次版本 / `ui.compose` 精确版本匹配；任一不满足只禁用该 Compose 绑定。
- Composer action 与 page 是 `PluginUiContributions` 的贡献而非 `SlotBinding`：动作直接渲染回调；页面内容走宿主约定槽位 `plugin.page:<pageId>`（`plugin-api` 的 `SlotIds` 暂未收录，属于待补契约，见 P10b 报告）。
- 自定义消息目前没有 app 侧消息模型：`CustomMessageRenderer` 接收 `PluginCustomMessage(messageId, customType, payload)`，由 Lead 在 chat 渲染层从 SDK `CustomMessageAppended` 事件接入；兜底卡把 payload 以 JSON 明文展示。
- 崩溃保护复用 `PluginFailureGuard`：key 为 `<pluginId>:<slotId>`，残留标记表示上次渲染导致进程死亡，下次启动该绑定走 fallback。
- 本目录 composable 都接收 `assetsDir`/resolver 而不是 AppGraph 依赖，方便 Lead 直接接线与 JVM 单测纯逻辑（`app/src/test/kotlin/.../plugin/host/ui/`）。
