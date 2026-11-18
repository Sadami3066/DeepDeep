# Mika / Phongent 屏幕执行源码

来源：mylea1/phongent，`cb58c10425a870d189ef39b8cc3dd1c8e8155d3e`（Apache-2.0，Copyright 2026 wangzan101）。

- `uistate/ActionEffect.kt`、`EffectTuning.kt`、`TreeQuality.kt`、`LimitNotice.kt`：原动作分类、阈值、节点树质量和上限提示。
- `uistate/UiTree.kt`、`PageInfo.kt` 原样保留；`EffectScreenModels.kt` 提取自 `ScreenPort.kt` 的数据定义。共享 `tool/Verb.kt` 位于 runtime。
- `uistate/Accuracy.kt`、`RefStabilizer.kt`、`InterfaceUpdate.kt`、`UiTreeJson.kt`、`ScreenText.kt`：原引用匹配、界面差分、树与节点协议。
- `agent/BatchRunner.kt`、`uistate/UntilWait.kt`、`tool/Until.kt`：原多步执行和条件等待；宿主适配见 `PhongentBatchTool.kt`。批处理仅调用屏幕工具，等待由宿主的显示屏事件、单调时钟和插话信号支持。
- `visual/FrameGrid.kt` 与 `FrameTuning.kt`：原始亮度网格采样和差分。
- 原资源读取与模板展开位于 runtime 的同名 `prompt` 包。
- `uistate/VisibleText.kt`：原识别文字降噪、同树文字去重、密码区域过滤及协议。Android 识别及稳帧实现位于同命名空间的 `cap/phone`。
- 原算法测试位于 `app/src/test/kotlin/com/wangzan101/mika/core`，`TreeQualityTest.kt` 包含纯树质量部分；其 OCR 与工具分发集成部分依赖未迁移的 Phongent 工具运行时。DeepDeep 的屏幕协议和 Android 适配位于宿主 `screen` 包。

Phongent 的网页工具、录制回放和图像理解入口属于其完整运行时。这里的调用端提供 DeepDeep 已有的 Android 屏幕事实。
