# ui/chat · 聊天界面

聊天首页与页面状态：`ChatScreen` 把 `ChatUiState` 渲染成消息列表与底部输入卡/面板，`ChatViewModel` 观察 `ConversationStore` 投影发射状态。消息/回合渲染、Markdown、输入卡与附件等被系统助手浮层、悬浮窗共用的组件都在 `ui/common/chat/`，本包只保留聊天页面本身。

## 文件

| 文件 | 职责 |
|---|---|
| `ChatScreen.kt` | 聊天主页面：消息列表、输入卡与底部面板、todo 折叠面板、空状态与回到底部；历史折叠（加载更早）与右侧消息导航条的接线 |
| `ChatHistoryNav.kt` | 历史折叠与导航窗口的纯逻辑：初始可见窗口、每次展开步长、当前消息的 tick 槽位、自动跟随时的窗口顶槽、消息与列表项下标换算 |
| `MessageNavBar.kt` | 右侧消息导航条：每条用户消息一个短横 tick 的可上下滑动 5 行窗口（当前消息 accent 高亮），窗口随聊天滚动逐条跟随，单击 tick 直达 |
| `ChatViewModel.kt` | 观察 ConversationStore 产出 ChatUiState，驱动发送/停止/切换/重试 |
| `ChatTopBar.kt` | 顶栏：菜单、定时/任务/用量/文件入口 |
| `UsageSheet.kt` | 会话用量统计 sheet |
| `ContextMeter.kt`、`JobsMeter.kt` | 输入卡上下文 token 环与顶栏后台任务计数 |
| `SlashPalette.kt` | `/` 命令与技能补全列表 |
| `SessionSearchHit.kt` | 会话搜索命中项（会话 + 工作区名） |
| `state/` | ViewModel 内部控制器：会话打开/列表/模型/工作区/自动重试 |

共享渲染组件在 [../common/chat/](../common/chat/)（`ChatTurn`、`ChatComposer`、附件/相机、Markdown、工具卡与交付卡等）；本包不再重复定义。

## 要点

- 状态单向：ChatViewModel 只把 `ConversationStore.observe()` 的投影、输入草稿、滚动锚点和界面元数据合成 ChatUiState；消息与运行态不在本包归约，也不另存。
- 共享组件改动要同时看系统助手浮层与悬浮窗：输入卡（`ChatComposer`/`ChatInputField`/`ComposerMetrics`）、`ChatTurn`、`rememberChatScrollController`、`attach`/`normalizeImages` 等由 `ui/common/chat` 提供，浮层与悬浮窗直接复用。
- 流式渲染：Markdown 经 `MarkdownStreamPlanner` 按稳定前缀分块缓存，只有新增内容才重新解析；改解析/分块要保住流式性能与代码块未闭合时的容错。
- 布局：输入卡与底部面板是消息区 Box 上 `align(BottomCenter)` 的浮层，列表按 `overlayHeightPx` 留底部 contentPadding；IME/面板变化靠根 Column 的 bottom inset 与 `ChatScrollController` 贴底；改成根 Column 布局兄弟会破坏浮层视觉（见 d2729d0d 回退）。
- todo 面板：`todo_write` 卡片进入回合 steps 但不渲染，payload 取最新 `CustomCard(todo_write)`（独立消息兜底），只在输入卡上方的 `TodoPanel` 展示（折叠摘要/进度/展开列表）；历史遗留的独立 todo 消息仍从消息流过滤。
- 历史折叠：超过 20 条消息只渲染最新窗口（初始 3 条，每次 +20），折叠窗口是 `rememberSaveable(conversationId)` 的界面状态，不写回会话；顶部「加载更早」与到顶下拉都走 `loadEarlier`，跳转用 `ChatScrollController.jumpToItem`；用户消息超过 5 条时右侧显示导航条：每条用户消息一个 tick、窗口一次显示 5 条，窗口随聊天滚动逐条滑动跟随（当前消息 accent 高亮），也可在导航条上上下滑动浏览全部 tick，单击 tick 直达对应消息并自动展开折叠窗口。
- 文案与视觉：用户可见字符串在 `res/values*/strings_*.xml`（`ConversationStrings` 只是运行时载体），只使用 `:design` 的 Dd* 组件与 `deepDeepColors`。
