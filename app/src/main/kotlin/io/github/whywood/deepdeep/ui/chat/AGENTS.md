# ui/chat · 聊天界面

聊天首页与页面状态：`ChatScreen` 把 `ChatUiState` 渲染成消息列表与浮在对话上的输入卡，`ChatViewModel` 观察 `ConversationStore` 投影发射状态。消息/回合渲染、Markdown、输入卡与附件等被系统助手浮层、悬浮窗共用的组件都在 `ui/common/chat/`，本包只保留聊天页面本身。

## 文件

| 文件 | 职责 |
|---|---|
| `ChatScreen.kt` | 聊天主页面：消息列表、输入卡叠层、todo 折叠面板、空状态与回到底部 |
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
- todo 面板：`todo_write` 的 custom message 只在输入卡上方的 `TodoPanel` 展示（折叠摘要/进度/展开列表），聊天流里过滤掉这类消息，不再内联渲染。
- 文案与视觉：用户可见字符串在 `res/values*/strings_*.xml`（`ConversationStrings` 只是运行时载体），只使用 `:design` 的 Dd* 组件与 `deepDeepColors`。
