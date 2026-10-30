# Contributing

本仓库当前为私有协作仓库，按「随时可开源」的标准维护。开始前先读 `AGENTS.md`（命令、基线、边界）与 `docs/dev/refactor/PLAYBOOK.md`（任务协议）。

## 工作方式

先选档（对话里说一句档位即可；定义见 `docs/dev/refactor/PLAYBOOK.md §0`）：

| 档 | 命令 | 流程 |
|---|---|---|
| **L0 文档** | `./scripts/gate.sh 0` | 免卡、免证据，可只写提交标题 |
| **L1 编译** | `./scripts/gate.sh 1` | 改代码先看能不能编过 |
| **L2 单测** | `./scripts/gate.sh 2` | 一卡、免 G2/证据 |
| **L3 全量（默认）** | `./scripts/gate.sh 3` | 一卡；G2 由 Lead 抽检；涉 UI 才要真机证据 |
| **L4 真机** | `./scripts/gate.sh 4` | 一卡；G2 强制；ACC 证据强制 |
| **CI** | `./scripts/gate.sh ci full` | 手动在 GitHub 跑，不阻塞本地 |

L2 及以上：建卡 → 短分支 `task/<id>-<slug>` → 跑对应档 → 提交 → 合入。

1. 从 `docs/dev/refactor/tasks.tsv` 取一张活跃卡（没有就先写一张），确认 `scope`/`accept`/`verify` 三列非空。
2. 建短分支：`task/<id>-<slug>`。
3. 只改任务卡白名单里的文件；需要额外文件先停下要授权。
4. 跑对应档位的 `./scripts/gate.sh <档>`，直到通过；把原始输出贴进 PR。
5. 提交标题：`type: 祈使句摘要 [任务ID]`；L2+ 的 body 三行：`验收:`、`回滚:`、`不在范围:`。
6. 开 PR，按模板填对应档位的段落（L2/L3 免 G2 段；L4 全填）。
7. squash 合并；出问题用 `git revert <sha>` 回滚，一次只回滚一个任务。

## 硬规则

- 门禁基线：1223 单测、0 lint error、156 warning、13 编译警告；数字见 `docs/dev/refactor/BASELINE.md`。
- 测试数下降、出现 skip、删断言、无法一条 revert 回滚、跨 >2 模块且无 ADR —— 直接退回。
- 不新增第三方依赖；不重排无关文件；代码里不留注释；用户可见文案走 `res/values*/strings_*.xml`。
- 涉及持久化/schema 改动，必须给「旧数据 → 新版本」的真机证据。
- 性能结论必须有 `gfxinfo` 前后数字，否则不算验收依据。

## 评审

三道闸门（G1 机器 / G2 证伪 / G3 人工验收）见 `docs/dev/refactor/PLAYBOOK.md §2`。

**G2 强制范围**：L4 必做；L3 由 Lead 抽检；L0–L2 不做。G2 必须至少产出一条「这个改动的假设可能不成立」，否则视为未评审。

## 文档

- 总索引：`docs/README.md`
- 用户向：`README.md`、`docs/user/GUIDE.md`
- 开发者向：`docs/dev/DEVELOPMENT.md`、`docs/dev/ARCHITECTURE.md`
- agent 向：`AGENTS.md`
- 决策记录：`docs/dev/adr/`
- 验收：`docs/dev/ACCEPTANCE.md`
- 重构期计划：`docs/dev/refactor/`
