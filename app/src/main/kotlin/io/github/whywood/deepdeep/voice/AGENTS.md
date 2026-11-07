# voice · 语音输入与朗读

包系统 `SpeechRecognizer` 与 `TextToSpeech`，向聊天输入卡和系统助手浮层提供事件流。识别语言跟随 App 语言偏好；何时开麦、识别后怎么处理由上层决定。

## 文件

| 文件 | 职责 |
|---|---|
| `ContinuousVoiceSession.kt` | 连续识别会话：自动重启、有限重试、PCM 音量 |
| `SpeechRecognizerController.kt` | 单次语音识别封装：事件流与音量 |
| `TextSpeaker.kt` | 系统 TTS 封装：语言回退与播放状态 |
| `SpeechEvents.kt` | 识别事件、错误模型与系统错误码映射 |
| `SpeechText.kt` | 朗读长文本按标点安全切段 |
| `VoiceLanguage.kt` | App 语言到 ASR 标签与 TTS 候选 locale |

## 要点

- 系统可能没有设置默认识别服务（`Settings.Secure.VOICE_RECOGNITION_SERVICE` 为空时 `SpeechRecognizer.createSpeechRecognizer` 会抛错）：`SpeechRecognizerController` 会按包管理器里已注册的 `RecognitionService` 依次回退（优先不要求 BIND 权限的服务），不再直接判为不可用。
- 连续识别是默认交互：静音或单次识别失败不退出，NO_MATCH/超时重启、网络类有限重试；主界面与系统助手浮层的麦克风语义必须一致（X 取消、停止追加、箭头发送）。
- 语言只有两个来源：`AppLanguage` 决定识别标签与 TTS 候选，`SYSTEM` 直接取默认 locale；不要另存一份语音语言设置。
- TTS 按标点切段串行播报，`TextSpeaker` 用 Compose state 暴露可用与播放中状态，释放由持有方在销毁时调用。
