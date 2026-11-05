# ui/plugins · 插件界面

插件列表/详情/导入/setup 全部由 `PluginManagerFacade` 的清单驱动状态渲染；不自持插件逻辑，不 import 插件实现类。

## 文件

| 文件 | 职责 |
|---|---|
| `PluginsScreen.kt` | 插件列表：来源分 tab、搜索、启停、安装、外部导入与检查更新 |
| `PluginDetailScreen.kt` | 详情：状态/版本/来源/发布者/技能/设置/动作/详情行、插件自绘详情区块、插件页入口 |
| `PluginSetupSheet.kt` | `plugin_setup` sheet：宿主外壳 + 可选插件自绘内容区（stage/url/qr/progress/cancel 兜底） |
| `PluginsStateHolder.kt` | 纯 UI 临时状态（搜索词、启停中 id）与命令分发（安装、启停、导入、更新） |
| `PluginImportDialogs.kt` | 外部插件导入确认/结果、更新列表 |
| `PluginSettingsSection.kt` | 新 `SettingSpec` 渲染（Toggle/Choice/Number/Text/Group、summary、requiresRestart） |
| `PluginUiSupport.kt` | LocalizedText/IconRef 解析、phase/tab/search/size 纯函数 |
| `PluginHostSlots.kt` | 插件 UI 槽位 CompositionLocal 与工具卡/详情/setup/页面/自定义消息槽包装 |

## 要点

- 插件列表/详情/忙碌/操作/交互都直接消费 `PluginManagerFacade.state`（`collectAsStateWithLifecycle`），本目录不镜像插件状态；`PluginsStateHolder` 只保留搜索词、启停中 id 与命令分发。
- 详情页在 manager 未加载完成时显示加载态，加载完成且插件确实不存在才返回；重装入口只对 `installable && installed`，删除入口只对 `installable` 且 `source == EXTERNAL`，不可安装的内置插件没有下载/重装/删除入口。
- `LocalPluginManager` 由 `MainActivity` 的 `ProvidePluginManager` 提供；无管理器时槽位一律回退宿主默认 UI。
- 槽位渲染失败由 `PluginFailureGuard` 记录并在下次启动禁用该绑定，禁止让插件 UI 异常影响主界面。
- 用户可见文案走 `res/values*/strings_plugins.xml`；颜色/字号只用 `:design` token。
