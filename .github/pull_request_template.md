## 这是什么

（三行以内人话；第一条写「用户会看到什么变化」）

## 档位（先选一档，定义见 `docs/dev/refactor/PLAYBOOK.md §0`）

- [ ] **L0 文档**：`gate.sh 0`；免卡、免证据，可只写标题
- [ ] **L1 编译**：`gate.sh 1`；看能不能编过
- [ ] **L2 单测**：`gate.sh 2`；一卡，免 G2/证据
- [ ] **L3 全量（默认）**：`gate.sh 3`；一卡，G2 由 Lead 抽检，涉 UI 才要证据
- [ ] **L4 真机**：`gate.sh 4`；一卡，G2 强制，ACC 证据强制
- [ ] 需要云端验证：`gate.sh ci fast` / `gate.sh ci full`（手动，不阻塞本地）

## 关联

任务卡: **[L2+ 填卡 ID]**　issue: #___　类型: feat / fix / refactor / perf / docs / test / chore / build

## 变更面（自动填，不手写）

```
git diff --name-only <base>..HEAD
```

文件数 ___ / 净增删 ___ 行 / 涉及模块 ___
是否 ⊆ 任务卡白名单：是 / 否 / L0-L1 无需（否 → 说明理由）

## 闸门输出（贴 `gate.sh` 原始输出）

- [ ] L0：`./scripts/gate.sh 0`
- [ ] L2+：`./scripts/gate.sh <档>` 通过，单测 ___ 条（基线 1165）、lint 0 error（警告 ___，基线 156）、编译警告 ___（基线 13）
- [ ] L2+：新增/修改行为有测试；**把修复回退后这些测试会红**（贴红的那次输出）
- [ ] 需要时：`./scripts/gate.sh ci full` 的 run 链接

## G2 证伪结论（L4 必填；L3 可留空由 Lead 抽检）

- 最可能在哪类设备或状态下崩：
- 哪些现有测试其实没覆盖：
- 哪部分照做会变糟：
- 判定：成立 / 部分成立 / 不成立（不成立必须写理由）

## G3 人工验收（L4 必填；L3 仅 UI 变化时填）

- [ ] 真机覆盖安装（不卸载）
- [ ] 验收条目：#___ ～ #___（`docs/dev/ACCEPTANCE.md`），逐条打勾，截图在 `docs/dev/evidence/<date>-<id>/`
- [ ] 回滚演练：`git revert --no-commit <sha> && ./scripts/gate.sh 3` 干净通过
- [ ] 未覆盖风险（写清楚「这条我没验」）：

## 回滚

```
git revert <sha>
adb install -r <上一个 tag 的 APK>
```

数据影响：无 / 涉及文件 ___ / 升级验证条目 #___
