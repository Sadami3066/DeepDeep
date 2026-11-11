# ui/common · 跨页面通用组件

跨页面复用的通用 UI 与能力组件：语音输入、OAuth 登录、厂商图标、头像、分享、相对时间、扫码策略。被聊天、系统助手浮层、设置与引导共用；这里不持有业务状态，状态由调用方传入或由同目录 Session/Store 提供。

## 文件

| 文件 | 职责 |
|---|---|
| `AvatarBadge.kt` | 圆形头像；无图时显示名字首字母 |
| `ComposerVoice.kt` | 输入卡语音：录音状态机、麦克风权限、音量/转写与提交 |
| `Haptics.kt` | 触感开关的 CompositionLocal（LocalHapticsEnabled） |
| `HostFilesProvider.kt` | 文件访问入口的 CompositionLocal（LocalHostFiles + ProvideHostFiles），由 App 根层注入 `graph.hostFiles` |
| `market/` | 市场页共用：`MarketSection.kt` 放市场 tab（加载/失败/离线/空态、条目行、详情弹层、预览卡片与全屏查看、安装）、`MarketLogic.kt` 放纯过滤/状态/体积格式化与权限档位比较、`MarketIconLoader.kt` 下载并缓存条目图标、`MarketPreviewLoader.kt` 加载并缓存插件预览图（内存 LRU、下采样、去重与失败重试）、`MarketPreviewLogic.kt` 放预览纯逻辑、`MarketAccessTag.kt` 画运行所需权限档位标签（普通/ADB/Root） |
| `OAuthLoginSession.kt` | OAuth 登录交互状态机：URL/提示/选择/输入，供 service 驱动 |
| `OAuthLoginSheet.kt` | OAuth 登录弹层：打开浏览器、等待、选择与验证码输入 |
| `ProviderIcon.kt` | 厂商图标：provider id 映射 drawable，缺图回退首字母 |
| `RelativeTime.kt` | 相对时间文案：刚刚/分钟/小时/昨天/天 |
| `ShareChooser.kt` | 分享 chooser，排除自身 Activity |
| `VoiceRecordingBar.kt` | 录音条：波形历史与取消/停止/发送 |
| `VoiceTranscriptPreview.kt` | 语音转写预览：已提交与临时文本、自动滚底 |
| `scan/` | 扫码策略 5 个文件：匹配规则、JSON、持久化、按策略拉起 App |
| `chat/` | 聊天/浮层/悬浮窗共用的渲染组件：消息回合、Markdown、工具卡与工具图标/简洁标题（`ToolPresentation.kt`）、交付卡、提问卡、插件槽位、todo 面板、输入卡与附件、相机扫码、`QrScan.kt`（扫码状态机与结果 sheet）、文件预览、滚动控制；`state/` 放草稿与附件导入 |

## 要点

- 语音共用：`rememberComposerVoice` 同时服务聊天输入卡与系统助手浮层，权限被永久拒绝时直接跳应用设置；改交互两边都要看。
- OAuth：`OAuthLoginSession` 是 runtime `OAuthInteraction` 的实现，`ProviderOAuthService.login` 在协程里驱动；关闭必须 `cancel()`，否则挂起的 prompt/select 不返回。
- 扫码策略：官方内置 `assets/scan/strategies.json`（只读），自定义落 `files/scan/strategies.json`；匹配优先级 scheme > host > regex > contains，`ScanLauncher` 打不开时回退复制。
- 用户可见文件预览/分享统一从 `LocalHostFiles` 取文件（Root 档文件先暂存到 cache）；不要直接用 `FileViewerDialog` 传入的路径做 `BitmapFactory`/`bufferedReader` 假设可读。预览/分享无法物化的超大文件显示「导出到下载目录」：ROOT 档由 `HostFiles` 导出到共享存储再分享，其余档写入系统下载目录。
- Markdown 内联图片：本地源先经 `readableMarkdownLocalFile`（`HostFiles.isFile` + 可读性，不可读则 `HostFiles.stage` 到 cache）再交给解码器；GIF/WebP 等动画按文件名判定后走 `AnimatedImage`，暂存副本沿用 `HostFiles` 的 1 小时清理，别在 UI 里用裸 `File.isFile` 判断。
- `ProviderIcon` 是静态映射，新增厂商要同时补 `res/drawable-nodpi/provider_*`（含 `-night`）与映射表，否则回退首字母。
