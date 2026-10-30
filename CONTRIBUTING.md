# Contributing

私有协作仓库，按「随时可开源」维护。规则只有一处：`AGENTS.md`（命令、边界、任务类型 → 档位、UI 轻量模式）。本文只讲协作流程。

## 流程按任务类型选，不搞一刀切

| 改什么 | 流程 |
|---|---|
| 文档 / 文案 | 直接改 → `./scripts/gate.sh 0` → 一个提交 |
| UI 布局 / 样式 / 动效 | 免卡 → `gate.sh 2` 编译 → `gate.sh 4` 截图（见 `AGENTS.md §6`） |
| 纯重构 / 补测试 | 一卡 → 分支 `task/<id>-<slug>` → `gate.sh 2` |
| 新功能 / 修 bug / 改行为 | 一卡 → 分支 → `gate.sh 3` |
| 数据 / 权限 / 启动顺序 / 跨模块契约 / 构建 | 一卡 + ADR + 数据影响说明 → `gate.sh 3/4` |

一卡 = `docs/dev/refactor/tasks.tsv` 一行（`scope`/`accept`/`verify` 三列非空）；完成卡移到 `tasks-archive-<yyyy-mm>.tsv`，不长期堆积。

## 步骤

1. 从 `tasks.tsv` 取卡；L0 与 UI 轻量模式可免卡。
2. 建短分支：`task/<id>-<slug>`。
3. 改动；只改卡上白名单里的文件（L0 / UI 轻量除外）。要多改文件先说。
4. 跑 `./scripts/gate.sh <档>` 直到通过，把原始输出贴进 PR。
5. 提交：`type: 摘要 [ID]`；L2+ 的 body 三行 `验收:` / `回滚:` / `不在范围:`。
6. 开 PR（模板 `.github/pull_request_template.md`），按档位只填必要段落。
7. squash 合并；回滚 `git revert <sha>`，一次只回滚一个任务。

## 两条额外硬规则（其余在 `AGENTS.md §4`）

- 持久化 / schema 改动必须给「旧数据 → 新版本」的真机证据。
- 性能结论必须有 `gfxinfo` 前后数字，否则不算验收。

## 评审强度

- L0–L2：作者自验即可。
- L3：Lead 抽检。
- L4：另一个只读 agent 做证伪 + ACC 截图证据（`docs/dev/refactor/PLAYBOOK.md §2`）。

## 文档

- 索引：`docs/README.md`（按需查，不要预读全树）
- 给 agent 的规则：`AGENTS.md`
- 完整协议（写卡 / 闸门 / 禁止动作 / 回滚演练）：`docs/dev/refactor/PLAYBOOK.md`
