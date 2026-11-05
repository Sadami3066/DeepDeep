# ui/workshop · 创意工坊页

创意工坊的界面与 WebView 宿主：把 agent 写出的 HTML/CSS/JS 目录渲染成隔离轻应用，页面经 bridge 读写自己的数据并调用一次性 agent。创作、文件存储与 bridge 协议在 runtime（WorkshopStore/WorkshopBridgeRouter），本包不直接读写创意目录。

## 文件

| 文件 | 职责 |
|---|---|
| `WorkshopScreen.kt` | 工坊列表：搜索/刷新/创建/删除、损坏条目标记与原因弹窗 |
| `WorkshopViewerScreen.kt` | 创意查看器：加载/重载/重命名/删除、错误与不支持降级 |
| `WorkshopWebView.kt` | Compose 内 WebView 宿主：资产服务、bridge、主题注入、崩溃重建 |
| `WorkshopStateHolder.kt` | 列表状态机：scan/过滤/删除/重命名、并发刷新合并、错误重试 |
| `WorkshopBridgeHost.kt` | JS↔原生桥传输：消息分发、事件批量与文本增量推送、页面注入 |
| `WorkshopAssetLoader.kt` | 创意目录的本地 HTTPS 资产服务（WebViewAssetLoader，支持 Range） |
| `WorkshopChatCard.kt` | 聊天交付卡：显示已创建创意，并从工具轨迹解析创意信息 |
| `WorkshopDialogs.kt` | 损坏创意的原因文案映射与删除对话框 |

## 要点

- 隔离方式：每个创意用 `<id>.workshop.local` 自定义域 + WebViewAssetLoader 提供资源，`file://` 不可用；需要 WEB_MESSAGE_LISTENER 与 DOCUMENT_START_SCRIPT，缺失时降级提示。
- 页面只能经 bridge 访问按创意隔离的 SQLite 数据与 `app.agent.*`；新能力加在 runtime 的 router/sdk，不在这里暴露原生对象。
- 事件推送在 `WorkshopBridgeHost` 按 40ms 批量合并 text/thinking 增量；改协议要同步 `assets/workshop/bridge.js`。
- 聊天里的工坊入口卡由 `ui/chat` 调 `parseRegisteredWorkshop`/`WorkshopChatCard`，工具名（workshop_load/register）变化会影响卡片显示。
