# floating · 跨应用悬浮窗

跨应用的悬浮层：小鲸鱼气泡、事件卡、运行面板与虚拟屏预览。只投影 runtime/提问/通知的状态；显示依赖系统悬浮窗权限，App 是否在前台只影响展示内容。

## 文件

| 文件 | 职责 |
|---|---|
| `FloatingWindowController.kt` | 悬浮窗总控：建窗、拖动、feed 接线、主题与预览 |
| `FloatingFeed.kt` | 收集 ConversationStore 事件、提问与运行状态，组装 feed 状态 |
| `FloatingFeedReducer.kt` | feed 状态归约与卡片队列、鲸鱼情绪等派生逻辑 |
| `FloatingPlacement.kt` | 气泡落位、卡片/面板/预览排布与尺寸计算 |
| `FloatingStyle.kt` | 悬浮窗尺寸、颜色与动效常量 |
| `FloatingBubble.kt` | 小鲸鱼气泡外观、情绪与环形进度 |
| `FloatingCard.kt` | 事件卡：提问、插件连接、完成与订阅跟踪 |
| `FloatingPanel.kt` | 运行面板：运行中、待处理提问、已完成 |
| `FloatingVirtualPreview.kt` | 虚拟屏缩略预览（在线/离线/失败态） |
| `FloatingPreview.kt` | 预览帧节流、缩放与宽高比计算 |
| `FloatingWindowHost.kt` | 悬浮窗内 ComposeView 的生命周期与状态宿主 |
| `FloatingContentFrame.kt` | 按屏幕尺寸与 insets 计算可用内容区域 |
| `FloatingTouchInterceptor.kt` | 触摸拦截容器，把事件先交给拖动/点击回调 |

## 要点

- 气泡与卡片/面板/预览是两扇 WindowManager 窗口；卡片、面板、预览共用一个 surface 窗口，同一时刻只显示一个 body，避让顺序由 `FloatingPlacement` 计算。
- 状态来自 `ConversationStore`（`activeRuns`/`events`）、`QuestionCenter.pending` 与 `ConversationQueries`（会话标题兜底）；唯一直接写操作是单选题在气泡/卡片内提交答案，其余交互都经回调转到 App 或系统助手浮层。
- 所有尺寸与配色在 `FloatingStyle.kt`：卡片/面板/预览整体 0.75 倍，预览默认 0.6 倍缩略；改布局先改 token。
