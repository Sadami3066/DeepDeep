# ui/chat · 聊天界面

聊天首页与消息流，App 最核心的界面包：`ChatScreen` 把 `ChatUiState` 渲染成消息列表和浮在对话上的输入卡，`ChatViewModel` 订阅 runtime 事件发射状态；Markdown 流式渲染、相机扫码、文件预览等重组件也在这里。会话与运行状态的唯一真相是 runtime 事件，本包不另存一份。

## 文件

### 页面与骨架

| 文件 | 职责 |
|---|---|
| `ChatScreen.kt` | 聊天主页面：消息列表、输入卡叠层、空状态与回到底部 |
| `ChatViewModel.kt` | 订阅 runtime 事件产出 ChatUiState，驱动发送/停止/切换/重试 |
| `ChatUiState.kt`、`ChatUiStrings.kt` | 聊天状态模型与本地化文案/运行错误分类 |
| `ChatTopBar.kt` | 顶栏：菜单、定时/任务/用量/文件入口 |

### 消息/回合渲染

| 文件 | 职责 |
|---|---|
| `ChatTurn.kt` | 单个回合渲染：用户气泡、assistant 正文、过程、交付、重试 |
| `ConversationReducer.kt`、`ConversationEdits.kt` | 会话状态模型、runtime 事件 reducer 与纯编辑操作 |
| `ProcessSection.kt`、`ReasoningCard.kt` | 过程区与思考卡：步骤列表、运行计时、折叠 |
| `RunErrorDetails.kt` | 运行失败文案本地化与原始错误详情 |
| `SelectionActions.kt`、`SelectionCoordinator.kt`、`ChatActions.kt` | 文本选择菜单、跨消息选择协调与剪贴板复制 |

### Markdown 与代码

| 文件 | 职责 |
|---|---|
| `ChatMarkdown.kt` | Markdown 渲染入口：流式分块、块渲染与脚注 |
| `MarkdownParser.kt`、`MarkdownBlockScanner.kt`、`MarkdownInlineScanner.kt` | Markdown 块级/行内扫描与解析成 AST |
| `MarkdownStreamPlanner.kt` | 流式增量规划：稳定前缀缓存，只重解析新增内容 |
| `MarkdownImage.kt`、`AnimatedImage.kt` | MD 内嵌图片与文件动图渲染（ImageDecoder） |
| `InlineCodeText.kt`、`CodeHighlighter.kt` | 行内代码/路径渲染与代码块按语言高亮 |

### 输入区与附件

| 文件 | 职责 |
|---|---|
| `ChatComposer.kt`、`ChatInputField.kt`、`ComposerMetrics.kt` | 浮起输入卡三态、输入框封装与两边共用的尺寸常量 |
| `AttachmentPicker.kt`、`AttachmentUi.kt` | 附件来源面板/系统选择器与附件卡片、预览 |
| `SlashPalette.kt`、`ChatCommands.kt`、`SkillInvocation.kt` | `/` 命令与技能补全、解析及 `/skill:` 前缀剥离 |

### 交付与工具卡片

| 文件 | 职责 |
|---|---|
| `PresentCard.kt` | present 交付卡：文件/图片/视频/HTML/创意解析与预览 |
| `ToolCard.kt`、`ToolNames.kt` | 工具调用步骤卡（结构化参数 `rawArguments`/结构化 `resultPayload` 渲染、复制、插件 `tool.card.content:<tool>` 自绘内容体与默认兜底）与 MCP 工具名显示 |
| `MediaSave.kt` | 媒体保存到下载与分享 |
| `FileViewer.kt`、`HtmlFileView.kt` | 本地文件预览对话框（文本/图片/源码）与 HTML WebView 预览 |
| `CameraCaptureSheet.kt`、`CameraBarcodeAnalyzer.kt` | 内嵌相机 sheet（拍照+扫码）与 ML Kit 条码分析 |
| `ScanResultCard.kt`、`ScanAppPickerOverlay.kt` | 扫码结果卡与未命中内容时选择其他 app |

### 会话/搜索/控制器

| 文件 | 职责 |
|---|---|
| `ModelPickerSheet.kt`、`UsageSheet.kt` | 模型/思考档选择与会话用量统计 sheet |
| `ContextMeter.kt`、`JobsMeter.kt` | 输入卡上下文 token 环与顶栏后台任务计数 |
| `ChatScrollController.kt` | 消息列表滚动锚点、会话切换恢复与「回到底部」 |
| `SessionSearchHit.kt` | 会话搜索命中项（会话 + 工作区名） |
| `state/` | ViewModel 内部控制器：会话打开/列表/草稿/缓存/模型/用量/工作区/重试/事件投影 |

## 要点

- 状态单向：ChatViewModel 从 `runtime.events` 归约出 ChatUiState，ChatScreen 只读状态并回传 `ChatScreenActions`；不要在这里加第二份会话真相（消息、busy、运行态都以 runtime 为准）。
- 流式渲染：Markdown 经 `MarkdownStreamPlanner` 按稳定前缀分块缓存，只有新增内容才重新解析；改解析/分块要保住流式性能与代码块未闭合时的容错。
- 与浮层共用：`ComposerChip`/`QueueStrip`（在 `ChatComposer.kt`）、`ChatInputField`、`ComposerMetrics`、`ChatTurn`、`rememberChatScrollController`、`ConversationState` 等同时被系统助手浮层复用，改签名要同步。
- 文案与视觉：用户可见字符串在 `res/values*/strings_*.xml`（`ChatUiStrings` 只是运行时载体），只使用 `:design` 的 Dd* 组件与 `deepDeepColors`。
