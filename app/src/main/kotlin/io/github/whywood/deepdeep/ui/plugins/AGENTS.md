# ui/plugins · 插件界面

插件列表/详情/导入/setup 全部由 `PluginManagerFacade` 的清单驱动状态渲染；不自持插件逻辑，不 import 插件实现类。

## 文件

| 文件 | 职责 |
|---|---|
| `PluginsScreen.kt` | 插件列表：来源分 tab、搜索、启停、安装、外部导入与检查更新 |
| `PluginDetailScreen.kt` | 详情页骨架与加载态：消费 manager 状态，组装 slot 绑定、detail sections、连接入口 |
| `PluginDetailContent.kt` | 详情滚动内容：摘要/状态元数据/详情行/设置/插件自绘 section/页面入口/槽位故障/lastError |
| `PluginDetailFooter.kt` | 详情底部：插件动作、安装/更新/重装/删除按钮、错误重试与删除确认框 |
| `PluginSetupSheet.kt` | `plugin_setup` sheet：宿主外壳 + 可选插件自绘内容区（stage/url/qr/progress/cancel 兜底） |
| `PluginsStateHolder.kt` | 纯 UI 临时状态（搜索词、启停中 id）与命令分发（安装、启停、导入、更新） |
| `PluginImportDialogs.kt` | 外部插件导入确认/结果、更新列表 |
| `PluginSettingsSection.kt` | 新 `SettingSpec` 渲染（Toggle/Choice/Number/Text/Group、summary、requiresRestart） |
| `PluginUiSupport.kt` | LocalizedText/IconRef 解析、phase/tab/search/size 纯函数 |
| `PluginHostSlots.kt` | 插件 UI 槽位 CompositionLocal 与工具卡/详情/setup/页面/自定义消息槽包装，全部经 `SlotRegistry` 解析 |
| `PluginSlotIssuesSection.kt` | 详情页「插件槽位」区：残留崩溃标记（可恢复）与被禁用的 Compose 绑定及原因；`detailSectionEntries`/`slotIdFromGuardKey` 等纯函数 |
| `PluginRecoverySection.kt` | 详情页「插件故障」区：`FAILED/DEGRADED + lastError` 时显示错误文本；外部插件额外显示「重试激活」，有 `previousVersion` 时显示「回滚到 <version>」；纯函数 `showsRecoverySection`/`rollbackTargetVersion`/`supportsActivationRecovery` |

## 要点

- 插件列表/详情/忙碌/操作/交互都直接消费 `PluginManagerFacade.state`（`collectAsStateWithLifecycle`），本目录不镜像插件状态；`PluginsStateHolder` 只保留搜索词、启停中 id 与命令分发。
- 详情页在 manager 未加载完成时显示加载态，加载完成且插件确实不存在才返回；重装入口只对 `installable && installed`，删除入口只对 `installable` 且 `source == EXTERNAL`，不可安装的内置插件没有下载/重装/删除入口。
- 详情 section 一律按 `slotId = plugin.detail.section:<sectionId>` 查询：`PluginManagerFacade.pluginSlotEntries(pluginId)` 取该插件全部绑定后按完整 slotId 分组，每个 section 单独渲染宿主槽；不再拿 pluginId 顶替 sectionId。
- `setupPluginIds` 里的插件（含外部 `.ddp`）在详情页显示「连接 / 配置」入口，调用 `PluginManagerFacade.connect(pluginId)` 拉起 `plugin_setup` 流程。
- `LocalPluginManager` 由 `MainActivity` 的 `ProvidePluginManager` 提供；无管理器时槽位一律回退宿主默认 UI。
- 槽位崩溃标记进入组合时写入、正常 dispose 时清除；残留标记只禁用对应绑定，详情页「插件槽位」区可手动恢复，同插件其他槽位不受影响。
- 插件相位 `FAILED`/`DEGRADED` 且 `lastError` 非空时显示「插件故障」区：内置插件只显示错误文本（恢复走详情页启用开关），外部插件显示「重试激活」调 `retryActivation(pluginId)`，且有 `previousVersion` 时显示「回滚到 <version>」调 `rollbackPlugin(pluginId)`；成功后刷新详情，失败在区内显示错误文本。
- 用户可见文案走 `res/values*/strings_plugins.xml`；颜色/字号只用 `:design` token。
