# app · 界面与装配

Android 应用层：唯一 Activity + Compose 界面、全局依赖装配、系统能力接线。界面不实现 agent 逻辑，状态与事件都来自 runtime；用户可见文案全部在 `res/values*/strings_*.xml`。

## 入口与装配

| 文件 | 职责 |
|---|---|
| `DeepDeepApplication.kt` | Application 启动：hidden API 豁免、创建 AppGraph、后台预热字体 |
| `AppGraph.kt` | 全局依赖装配：偏好/凭据/runtime host/插件/权限/工具/Linux 环境，实现 AppDependencies；插件装配拆到 `graph/` |
| `MainActivity.kt` | 唯一 Activity：edge-to-edge、语言包装、深链接与分享入口、通知权限、拉起前台服务 |
| `DeepDeepApp.kt` | Compose 根：引导门控 → 主壳（抽屉/导航/聊天）、全局浮层与对话框 |
| `core/AppDependencies.kt` | 按用途切片的依赖接口（Runtime/Storage/Feature/Service/Advanced），Screen 只取所需，便于测试替身 |
| `core/ModelSelection.kt` | 默认模型校正：当前默认厂商没有凭据时自动切到首个已配置厂商 |
| `deeplink/DeepLink.kt` | `deepdeep://` 深链接解析（会话、设置、插件、工坊、定时等） |

## 包

| 包 | 职责 |
|---|---|
| [graph/](src/main/kotlin/io/github/whywood/deepdeep/graph/AGENTS.md) | 装配子图：相关全局依赖的构造与接线从 AppGraph 拆出（当前为插件装配 `PluginGraph`） |
| [ui/](src/main/kotlin/io/github/whywood/deepdeep/ui/AGENTS.md) | 全部 Compose 页面与组件、导航、抽屉 |
| [data/](src/main/kotlin/io/github/whywood/deepdeep/data/AGENTS.md) | 偏好、附件、技能、迁移等本地存储 |
| [plugin/](src/main/kotlin/io/github/whywood/deepdeep/plugin/AGENTS.md) | 插件契约与生命周期、外部/内置插件 |
| [service/](src/main/kotlin/io/github/whywood/deepdeep/service/AGENTS.md) | 前台服务、通知中心、无障碍服务 |
| [floating/](src/main/kotlin/io/github/whywood/deepdeep/floating/AGENTS.md) | 跨应用悬浮窗（气泡、卡片、运行面板） |
| [assistant/](src/main/kotlin/io/github/whywood/deepdeep/assistant/AGENTS.md) | 系统助手浮层（默认助手唤起） |
| [screen/](src/main/kotlin/io/github/whywood/deepdeep/screen/AGENTS.md) | 主屏/虚拟屏统一屏幕后端与节点树 |
| [vscreen/](src/main/kotlin/io/github/whywood/deepdeep/vscreen/AGENTS.md) | 虚拟屏特权 helper（协议、传输、引擎） |
| [adb/](src/main/kotlin/io/github/whywood/deepdeep/adb/AGENTS.md) | 内嵌无线调试 ADB 守护、配对与桥接 |
| [privilege/](src/main/kotlin/io/github/whywood/deepdeep/privilege/AGENTS.md) | 权限三档（普通/ADB/Root）解析与控制器 |
| [voice/](src/main/kotlin/io/github/whywood/deepdeep/voice/AGENTS.md) | 语音识别与朗读（ASR/TTS） |
| `res/` | `strings_*.xml` 按功能分文件、默认中文 + `values-en` 镜像；drawable 图标 |

## 要点

- 新页面：`ui/navigation/` 加 `AppDestination` 与 `AppRouteContent` 分支，Screen 放 `ui/<feature>/`，状态放同目录 ViewModel/Holder；包级 import 受 `verifyPackageBoundaries` 约束（feature 之间不互引，`common`/`navigation` 例外，ui 不许 import AppGraph/DeepDeepApplication）。
- 界面不直接调用 dev.pi SDK，一切经 runtime 的 `AgentRuntimeHost` 与其事件流；插件工具、MCP 工具由 runtime 注册，UI 只渲染。
- 用户可见文案一律进 `res/values*/strings_*.xml`（默认中文，`values-en` 同步）；插件文案走 `AndroidPluginStrings`，不在 Kotlin 里拼中文。
- 测试在 `src/test/kotlin` 镜像包结构（JUnit4 + coroutines-test，替身优先）：状态与纯逻辑有单测，界面外观靠手机截图验收；`src/androidTest/` 是设备测试（工具契约、图片/头像、日历），需连真机跑。
