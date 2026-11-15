# Phongent 守卫、反馈与退出核验

来源 mylea1/phongent `cb58c10425a870d189ef39b8cc3dd1c8e8155d3e`。

- `guard/OperationGuards.kt` 和 `guard/GuardRules.kt` 保留原文件。原测试在 runtime 同路径的测试源树。
- `prompt/Prompts.kt` 和 `prompt/Feedback.kt` 加载原 `effect.*` 与 `guard.*` 字段。仅加载所用的 `tools.md`，省略未用的 `loop.md`。
- `agent/BeforeAccept.kt`、`verify/AnswerSources.kt`、`verify/EvidenceLedger.kt`、`verify/Grounding.kt`、`verify/Verifier.kt`、`observation/Observation.kt`、`record/Redactor.kt` 保留原文件。随 Verifier 迁移 `model/` 和 `vision/` 的直接依赖及 `prompts/roles/verifier.md`。
- `agent/AnswerReview.kt` 将原 ToolDispatcher 换为用于读取证据的回调。核查使用的两个 RunEvent 移到 `record/AnswerReviewEvent.kt`，逻辑和文本保持上游行为。生产接入在 `io.github.whywood.deepdeep.runtime.PhongentGuiAnswerReview`。
- 原始 `VerifierTest`、`VerifierSourcesTest` 及四组 `Grounding*Test` 位于 runtime 对应测试源树。原 `AgentLoopTest` 的 FakeModel 和测试图像替身也直接迁移。
