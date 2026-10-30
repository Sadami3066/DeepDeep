# Contributing

本仓库当前为私有协作仓库，按「随时可开源」的标准维护。开始前先读 `AGENTS.md`（命令、基线、边界）与 `docs/dev/refactor/PLAYBOOK.md`（任务协议）。

## 工作方式

先按 `docs/dev/refactor/PLAYBOOK.md §0` 选档（改动越大档越高，拿不准就升档）：

| 档 | 做什么 |
|---|---|
| **T0 文档/文案** | 免卡。改完跑 `./scripts/check-docs.sh`，提交即可（CI 只跑文档检查）；body 可省略 |
| **T1 普通代码** | 建卡 → 短分支 → `./scripts/verify.sh` → 提交；G2 由 Lead 抽检，涉 UI 才要真机证据 |
| **T2 行为/数据/UI** | 建卡 → 短分支 → `verify.sh` → G2 证伪 → 真机证据 → 提交 |

T1/T2 的步骤：

1. 从 `docs/dev/refactor/tasks.tsv` 取一张卡（没有就先写一张），确认 `scope`/`accept`/`verify` 三列非空。
2. 建短分支：`task/<id>-<slug>`。
3. 只改任务卡白名单里的文件；需要额外文件先停下要授权。
4. 跑 `./scripts/verify.sh`，直到全绿；把原始输出贴进 PR。
5. 提交标题：`type: 祈使句摘要 [任务ID]`；body 三行：`验收:`、`回滚:`、`不在范围:`。
6. 开 PR，按模板填对应档位的段落（T1 免 G2/G3；T2 全填）。
7. squash 合并；出问题用 `git revert <sha>` 回滚，一次只回滚一个任务。

## 硬规则

- 门禁基线：1165 单测、0 lint error、156 warning、13 编译警告；数字见 `docs/dev/refactor/BASELINE.md`。
- 测试数下降、出现 skip、删断言、无法一条 revert 回滚、跨 >2 模块且无 ADR —— 直接退回。
- 不新增第三方依赖；不重排无关文件；代码里不留注释；用户可见文案走 `res/values*/strings_*.xml`。
- 涉及持久化/schema 改动，必须给「旧数据 → 新版本」的真机证据。
- 性能结论必须有 `gfxinfo` 前后数字，否则不算验收依据。

## 评审

三道闸门（G1 机器 / G2 证伪 / G3 人工验收）见 `docs/dev/refactor/PLAYBOOK.md §2`。

**G2 强制范围**：T2 必做；T1 由 Lead 抽检；T0 不做。G2 必须至少产出一条「这个改动的假设可能不成立」，否则视为未评审。

## 文档

- 总索引：`docs/README.md`
- 用户向：`README.md`、`docs/user/GUIDE.md`
- 开发者向：`docs/dev/DEVELOPMENT.md`、`docs/dev/ARCHITECTURE.md`
- agent 向：`AGENTS.md`
- 决策记录：`docs/dev/adr/`
- 验收：`docs/dev/ACCEPTANCE.md`
- 重构期计划：`docs/dev/refactor/`
