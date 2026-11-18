# ui/miniapp · 小程序页

小程序的界面与 WebView 宿主：把 agent 写出的 HTML/CSS/JS 目录渲染成隔离轻应用，页面经 bridge 读写自己的数据并调用一次性 agent。创作、文件存储与 bridge 协议在 runtime（MiniappStore/MiniappBridgeRouter），本包不直接读写小程序目录。

## 文件

| 文件 | 职责 |
|---|---|
| `MiniappScreen.kt` | 小程序列表：搜索/刷新/创建/删除、损坏条目标记与原因弹窗 |
| `MiniappViewerScreen.kt` | 小程序查看器：加载/重载/重命名/删除、添加到桌面、全屏切换（按小程序记忆，返回键退出）、错误与不支持降级 |
| `MiniappShortcut.kt` | 桌面快捷方式：生成 emoji 图标、请求系统 pin、状态复查与权限设置引导 |
| `MiniappWebView.kt` | Compose 内 WebView 宿主：资产服务、bridge、主题注入、崩溃重建 |
| `MiniappStateHolder.kt` | 列表状态机：scan/过滤/删除/重命名、并发刷新合并、错误重试 |
| `MiniappBridgeHost.kt` | JS↔原生桥传输：消息分发、事件批量与文本增量推送、`dispatchPush` 推送（theme/visibility/open）、页面注入 |
| `MiniappHostImpl.kt` | `MiniappHostGateway` 实现：env、文件、分享、剪贴板、通知提醒、媒体、语音、链接、定位、日历与触感/常亮 |
| `MiniappFileStore.kt` | 小程序文件区：相对路径校验、导入/读写/列目录/删除/导出、`/__files/` 本地地址、32MB 单文件与 100MB 总量限制 |
| `MiniappNotifications.kt` | 通知与定时提醒：频道、通知投递、提醒落盘（`files/miniapp-reminders.json`）、AlarmManager 调度、`MiniappReminderReceiver` 到点投递与开机重排 |
| `MiniappInteractions.kt` | 能力交互队列与 overlay：系统权限、文件选择、相册/视频、拍照、扫码、导出保存 |
| `MiniappViewerPreferences.kt` | 查看器偏好（全屏按小程序记忆）与 `MiniappLaunchEvents`（通知/深链打开参数） |
| `MiniappAssetLoader.kt` | 小程序目录与文件区的本地 HTTPS 资产服务（WebViewAssetLoader，支持 Range 与 `/__files/`） |
| [MiniappChatCard.kt](../common/chat/MiniappChatCard.kt) | 聊天交付卡（在 `ui/common/chat`）：显示已创建小程序，并从工具轨迹解析小程序信息 |
| `MiniappDialogs.kt` | 损坏小程序的原因文案映射与删除对话框 |

## 要点

- 隔离方式：每个小程序用 `<id>.miniapp.local` 自定义域 + WebViewAssetLoader 提供资源，`file://` 不可用；需要 WEB_MESSAGE_LISTENER 与 DOCUMENT_START_SCRIPT，缺失时降级提示。
- 页面只能经 bridge 访问按小程序隔离的 SQLite 数据、文件区与 `app.agent.*`；新能力加在 runtime 的 router/sdk，不在这里暴露原生对象。
- 能力实现都在本包：宿主能力装配在 `MiniappHostImpl`，交互经 `MiniappInteractions` 队列由 `MiniappInteractionOverlay` 拉起系统组件；提醒由 `MiniappReminderReceiver` 在进程外投递。
- 全屏只由查看器顶栏切换并记忆，系统返回键先退全屏；页面通过 `--dd-safe-*` 变量与 `app.env().safeArea` 自行留白。
- 事件推送在 `MiniappBridgeHost` 按 40ms 批量合并 text/thinking 增量；改协议要同步 `assets/miniapp/bridge.js`。
- 聊天里的小程序入口卡由 `ui/chat` 调 `parseRegisteredMiniapp`/`MiniappChatCard`，工具名（miniapp_load/register）变化会影响卡片显示。
- 添加到桌面用系统 pinned shortcut + `deepdeep://miniapp/<id>` 深链直达查看页；请求后复查 pinned 状态，未生效时给一次「去设置」引导（打开系统应用详情页），不反复打扰；小程序删除后点快捷方式会提示并返回列表。
