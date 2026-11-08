# ui/common · 跨页面通用组件

跨页面复用的通用 UI 与能力组件：语音输入、OAuth 登录、厂商图标、头像、分享、相对时间、扫码策略。被聊天、系统助手浮层、设置与引导共用；这里不持有业务状态，状态由调用方传入或由同目录 Session/Store 提供。

## 文件

| 文件 | 职责 |
|---|---|
| `AvatarBadge.kt` | 圆形头像；无图时显示名字首字母 |
| `ComposerVoice.kt` | 输入卡语音：录音状态机、麦克风权限、音量/转写与提交 |
| `Haptics.kt` | 触感开关的 CompositionLocal（LocalHapticsEnabled） |
| `market/` | 市场页共用：`MarketSection.kt` 放选项卡下标常量、预设过滤后的行渲染与「即将开放」提示 |
| `OAuthLoginSession.kt` | OAuth 登录交互状态机：URL/提示/选择/输入，供 service 驱动 |
| `OAuthLoginSheet.kt` | OAuth 登录弹层：打开浏览器、等待、选择与验证码输入 |
| `ProviderIcon.kt` | 厂商图标：provider id 映射 drawable，缺图回退首字母 |
| `RelativeTime.kt` | 相对时间文案：刚刚/分钟/小时/昨天/天 |
| `ShareChooser.kt` | 分享 chooser，排除自身 Activity |
| `VoiceRecordingBar.kt` | 录音条：波形历史与取消/停止/发送 |
| `VoiceTranscriptPreview.kt` | 语音转写预览：已提交与临时文本、自动滚底 |
| `scan/` | 扫码策略 5 个文件：匹配规则、JSON、持久化、按策略拉起 App |
| `chat/` | 聊天/浮层/悬浮窗共用的渲染组件：消息回合、Markdown、工具卡、交付卡、提问卡、插件槽位、todo 面板、输入卡与附件、相机扫码、文件预览、滚动控制；`state/` 放草稿与附件导入 |

## 要点

- 语音共用：`rememberComposerVoice` 同时服务聊天输入卡与系统助手浮层，权限被永久拒绝时直接跳应用设置；改交互两边都要看。
- OAuth：`OAuthLoginSession` 是 runtime `OAuthInteraction` 的实现，`ProviderOAuthService.login` 在协程里驱动；关闭必须 `cancel()`，否则挂起的 prompt/select 不返回。
- 扫码策略：官方内置 `assets/scan/strategies.json`（只读），自定义落 `files/scan/strategies.json`；匹配优先级 scheme > host > regex > contains，`ScanLauncher` 打不开时回退复制。
- `ProviderIcon` 是静态映射，新增厂商要同时补 `res/drawable-nodpi/provider_*`（含 `-night`）与映射表，否则回退首字母。
