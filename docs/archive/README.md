# 归档

这里放**不再维护**的文档：内容停止更新、可能已与代码漂移，保留价值是「当时怎么做的、为什么这么做」。当前文档入口见 [`../README.md`](../README.md)。

归档不等于删除：任何被归档的决策输入，都应能在 `docs/dev/adr/` 或 `docs/dev/refactor/REPORT.md` 找到现行结论；找不到就说明归档得太早，要把它放回去。

## 归档于 2026-10-30（M0–M3 重构完成时）

| 文档 | 是什么 | 为什么归档 | 现行替代 |
|---|---|---|---|
| [`STATUS.md`](STATUS.md) | round1–round43 逐轮开发与真机验证流水（602 行） | 重构前的开发日记，条目已被验收 ID 取代 | [`dev/ACCEPTANCE.md`](../dev/ACCEPTANCE.md) |
| [`PLAN.md`](PLAN.md) | P0–P20 早期阶段计划，29 条已完成 | 已完成，未来项被重构路线取代 | [`dev/refactor/README.md`](../dev/refactor/README.md) |
| [`design-notes.md`](design-notes.md) | 2026-10 实现细节笔记（296 行） | 实现细节漂移；结构与不变量已收敛进架构文档 | [`dev/ARCHITECTURE.md`](../dev/ARCHITECTURE.md) |
| [`LEGACY.md`](LEGACY.md) | 旧仓库恢复记录（bundle 与 sha256） | 现仓库已不依赖旧仓库，仅存 provenance | — |
| [`ROOT.md`](ROOT.md) | root / Android 高级能力调研（结论：不实现） | 研究结论已稳定，短期不做 | [`dev/adr/0002-no-permission-approval.md`](../dev/adr/0002-no-permission-approval.md) |
| [`research/`](research/) | 页面状态持久化、导航动效、Android 实践调研 | 结论已落进代码与 ADR | [`dev/adr/0003-single-activity-custom-navigation.md`](../dev/adr/0003-single-activity-custom-navigation.md) |
| [`device-testing-rounds.md`](device-testing-rounds.md) | 真机验收的 round-by-round 历史记录 | 现行条目见验收清单；操作步骤留在设备测试文档 | [`dev/ACCEPTANCE.md`](../dev/ACCEPTANCE.md)、[`dev/DEVICE_TESTING.md`](../dev/DEVICE_TESTING.md) |

## 约定

- 归档文件**只做路径适配，不改内容**；新发现的事实写进现行文档，不回头改归档。
- 需要复活某篇归档（例如重新评估 root 方案）：复制到 `docs/dev/`，按现行标准重写并更新索引，原归档保留。
