# ui/plugins · 插件界面

插件列表、导入与插件自绘页面装配；消费 `PluginManagerFacade` 的清单驱动状态渲染，不自持插件逻辑，不 import 插件实现类。

## 文件

| 文件 | 职责 |
|---|---|
| `PluginsScreen.kt` | 插件页：搜索、64dp 顶栏（导入「+」与「⋯」菜单：检查更新 / 插件源 / 刷新）、更新提示条、单列表行（图标+名称+摘要+开关+「⋯」动作菜单）、插件详情底部弹层、卸载确认弹窗 |
| `PluginsStateHolder.kt` | 纯 UI 临时状态（搜索词、启停中 id）与命令分发（启停、卸载、导入/更新、插件源、恢复） |
| `PluginImportDialogs.kt` | 外部插件导入确认/结果、更新列表、插件源设置弹窗 |
| `PluginUiSupport.kt` | LocalizedText/IconRef 解析、图标按显示尺寸降采样解码（缺失/损坏回退占位）、phase/search/size 纯函数、列表分组卡片 |
| `PluginHostSlots.kt` | `PluginPageHost`：按 `manager.pagesOf(pluginId)` 找页面并直接渲染 `PluginPage.content`（UI 不兼容/页面不存在时回退提示）与 composer 动作查询 |

## 要点

- 插件页只有一个列表，没有内外 tab；无插件时是居中空态（图标 + 提示 + 顶栏导入说明）。
- 列表行 = 图标 + 名称 + 摘要 + `DdSwitch`；行尾「⋯」菜单只放动作：「插件信息」、`FAILED`/`DEGRADED` 且 `lastError` 非空时的「重试激活」与「回滚到 <version>」（调 `retryActivation`/`rollbackPlugin`，失败文本由 `lastError` 回显在行内）、最后是破坏性「卸载」。只读信息不下拉菜单。
- 插件详情是 `DdSheet` 底部弹层（`plugin_detail`）：状态、版本、已安装版本（与当前版本不同时）、可更新版本、发布者、来源（外部导入/内置）、大小、技能；只读无动作。
- 全局「检查更新」在顶栏「⋯」菜单里；`state.availableUpdates` 非空时搜索框下出现可点的更新提示条，打开更新列表弹窗。
- 有 `pages` 的插件点整行进入第一个页面（`AppDestination.PluginPage`）；没有 `pages` 的行不可点。插件页面是插件唯一的自绘界面，内容由 `PluginPage.content` 内联提供，宿主用 `PluginPageHost` 直接渲染；UI 不兼容或页面不存在时显示不可用提示。
- 图标：asset 图标按实际显示尺寸 `inSampleSize` 降采样解码、不染色；文件缺失或损坏回退 `DdIcons.Puzzle`。
- `LocalPluginManager` 由 `MainActivity` 的 `ProvidePluginManager` 提供；无管理器时槽位一律静默不渲染。
- 槽位渲染不做崩溃隔离：插件内容抛异常会带崩 App；UI 不兼容或页面不存在时只禁用该插件 UI，不影响工具/技能。
- 用户可见文案走 `res/values*/strings_plugins.xml`（中英同步）；颜色/字号只用 `:design` token。
